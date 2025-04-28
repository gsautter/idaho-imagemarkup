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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.DocumentRoot;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.AnnotationFilter;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImFont;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImSupplement;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.gamta.ImDocumentRoot;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Utility library for modifying Image Markup documents.
 * 
 * @author sautter
 */
public class ImUtils implements ImagingConstants {
	
	/** Comparator sorting <code>ImWord</code>s in layout order, i.e., top to
	 * bottom and left to right. With arrays or collections whose content
	 * objects are NOT <code>ImWord</code>s, using this comparator results in
	 * <code>ClassCastException</code>s.<br>
	 * Because <code>Arrays.sort()</code> and <code>Collections.sort()</code>
	 * require a total order as of Java 1.7, this <code>Comparator</code> only
	 * compares the center points of words. Where a more line oriented ordering
	 * is required, use the <code>sortLeftRightTopDown()</code> method of this
	 * class instead. If this <code>Comparator</code> is handed as an argument
	 * to any method of this class, those methods defer to the aforementioned
	 * one. */
	public static Comparator leftRightTopDownOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord w1 = ((ImWord) obj1);
			ImWord w2 = ((ImWord) obj2);
//			
//			//	check non-overlapping cases first
//			if (w1.bounds.bottom <= w2.bounds.top)
//				return -1;
//			if (w2.bounds.bottom <= w1.bounds.top)
//				return 1;
//			if (w1.bounds.right <= w2.bounds.left)
//				return -1;
//			if (w2.bounds.right <= w1.bounds.left)
//				return 1;
//			
//			//	now, we have overlap (more likely within lines than between)
//			if (w1.centerY <= w2.bounds.top)
//				return -1;
//			if (w2.centerY <= w1.bounds.top)
//				return 1;
//			if (w1.centerX <= w2.bounds.left)
//				return -1;
//			if (w2.centerX <= w1.bounds.left)
//				return 1;
//			
//			//	now, either center lies in the other box (massive overlap, not peripheral)
			return ((w1.centerY == w2.centerY) ? (w1.centerX - w2.centerX) : (w1.centerY - w2.centerY));
		}
	};
	
	/** Counterpart of <code>leftRightTopDownOrder</code> for words written in
	 * bottom-up direction */
	public static Comparator bottomUpLeftRightOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord w1 = ((ImWord) obj1);
			ImWord w2 = ((ImWord) obj2);
			return ((w1.centerX == w2.centerX) ? (w2.centerY - w1.centerY) : (w1.centerX - w2.centerX));
		}
	};
	
	/** Counterpart of <code>leftRightTopDownOrder</code> for words written in
	 * top-down direction */
	public static Comparator topDownRightLeftOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord w1 = ((ImWord) obj1);
			ImWord w2 = ((ImWord) obj2);
			return ((w1.centerX == w2.centerX) ? (w1.centerY - w2.centerY) : (w2.centerX - w1.centerX));
		}
	};
	
	/** Counterpart of <code>leftRightTopDownOrder</code> for words written in
	 * right-left direction and upside down */
	public static Comparator rightLeftBottomUpOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord w1 = ((ImWord) obj1);
			ImWord w2 = ((ImWord) obj2);
			return ((w1.centerY == w2.centerY) ? (w2.centerX - w1.centerX) : (w2.centerY - w1.centerY));
		}
	};
	
	/** Comparator sorting <code>ImWord<code>s in text stream order.
	 * <code>ImWord</code>s belonging to different logical text streams are
	 * compared based on their text stream ID. With arrays or collections whose
	 * content objects are NOT <code>ImWord</code>s, using this comparator
	 * results in <code>ClassCastException</code>s. */
	public static Comparator textStreamOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord imw1 = ((ImWord) obj1);
			ImWord imw2 = ((ImWord) obj2);
			
			//	quick check
			if (imw1 == imw2)
				return 0;
			
			//	same text stream, compare page ID and position
			if (imw1.getTextStreamId().equals(imw2.getTextStreamId()))
//				return ((imw1.pageId == imw2.pageId) ? (imw1.getTextStreamPos() - imw2.getTextStreamPos()) : (imw1.pageId - imw2.pageId));
				return (imw1.getTextStreamPos() - imw2.getTextStreamPos());
			
			//	parse page IDs off text stream IDs and compare them
			String tsId1 = imw1.getTextStreamId();
			int tshPid1 = Integer.parseInt(tsId1.substring(0, tsId1.indexOf('.')));
			String tsId2 = imw2.getTextStreamId();
			int tshPid2 = Integer.parseInt(tsId2.substring(0, tsId2.indexOf('.')));
			if (tshPid1 != tshPid2)
				return (tshPid1 - tshPid2);
			
			//	parse head bounding boxes off text stream IDs and compare left and top
			BoundingBox tshBb1 = BoundingBox.parse(tsId1.substring(tsId1.indexOf('.') + ".".length()));
			BoundingBox tshBb2 = BoundingBox.parse(tsId2.substring(tsId2.indexOf('.') + ".".length()));
			return ((tshBb1.top == tshBb2.top) ? (tshBb1.left - tshBb2.left) : (tshBb1.top - tshBb2.top));
//			
//			//	check text stream IDs first
//			int c = imw1.getTextStreamId().compareTo(imw2.getTextStreamId());
//			if (c != 0)
//				return c;
//			
//			//	check page IDs
//			c = (imw1.pageId - imw2.pageId);
//			if (c != 0)
//				return c;
//			
//			//	check text stream position
//			return (imw1.getTextStreamPos() - imw2.getTextStreamPos());
		}
	};
	
	/** Comparator sorting <code>ImLayoutObject</code>s left to right with
	 * regard to their center points. With arrays or collections whose content
	 * objects are NOT <code>ImLayoutObject</code>s, using this comparator
	 * results in <code>ClassCastException</code>s. */
	public static final Comparator leftRightOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
			return ((ilo1.bounds.left + ilo1.bounds.right) - (ilo2.bounds.left + ilo2.bounds.right));
		}
	};
	
	/** Comparator sorting <code>ImLayoutObject</code>s top to bottom with
	 * regard to their center points. With arrays or collections whose content
	 * objects are NOT <code>ImLayoutObject</code>s, using this comparator
	 * results in <code>ClassCastException</code>s. */
	public static final Comparator topDownOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
			return ((ilo1.bounds.top + ilo1.bounds.bottom) - (ilo2.bounds.top + ilo2.bounds.bottom));
		}
	};
	
	
	/** Comparator sorting <code>ImLayoutObject</code>s in descending order
	 * by the area of their bounding boxes. With arrays or collections whose
	 * content objects are NOT <code>ImLayoutObject</code>s, using this
	 * comparator results in <code>ClassCastException</code>s. */
	public static final Comparator sizeOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
//			return (this.getSize(ilo2.bounds) - this.getSize(ilo1.bounds));
			return (ilo2.bounds.getArea() - ilo1.bounds.getArea());
		}
//		private final int getSize(BoundingBox bb) {
//			return ((bb.right - bb.left) * (bb.bottom - bb.top));
//		}
	};
	
	/**
	 * Sorts an array of words in left-right-top-down order, in two steps. This
	 * facilitates use of this topological order without the need to define it
	 * in a single <code>Comparator</code>, which is not generally possible in
	 * all cases, thus violating the contracts of <code>Arrays.sort()</code>.
	 * @param words the array of words to sort
	 */
	public static void sortLeftRightTopDown(ImWord[] words) {
		
		//	sort top-down first
		Arrays.sort(words, topDownOrder);
		
		//	sort individual lines left to right
		int lineStart = 0;
		for (int w = 1; w < words.length; w++)
			
			//	this word starts a new line, sort previous one
			if ((words[w].centerY > words[lineStart].bounds.bottom) && (words[lineStart].centerY < words[w].bounds.top)) {
				Arrays.sort(words, lineStart, w, leftRightOrder);
				lineStart = w;
			}
		
		//	sort last line
		Arrays.sort(words, lineStart, words.length, leftRightOrder);
	}
	
	/**
	 * Sorts an array of bottom-up words in bottom-up-left-right order, in two
	 * steps. This facilitates use of this topological order without the need
	 * to define it in a single <code>Comparator</code>, which is not generally
	 * possible in all cases, thus violating the contracts of <code>Arrays.sort()</code>.
	 * @param words the array of words to sort
	 */
	public static void sortBottomUpLeftRight(ImWord[] words) {
		
		//	sort left-right first
		Arrays.sort(words, leftRightOrder);
		
		//	sort individual lines bottom to top
		int lineStart = 0;
		for (int w = 1; w < words.length; w++)
			
			//	this word starts a new line, sort previous one
			if ((words[w].centerX > words[lineStart].bounds.right) && (words[lineStart].centerX < words[w].bounds.left)) {
				Arrays.sort(words, lineStart, w, Collections.reverseOrder(topDownOrder));
				lineStart = w;
			}
		
		//	sort last line
		Arrays.sort(words, lineStart, words.length, Collections.reverseOrder(topDownOrder));
	}
	
	/**
	 * Sorts an array of top-down words in top-down-right-left order, in two
	 * steps. This facilitates use of this topological order without the need
	 * to define it in a single <code>Comparator</code>, which is not generally
	 * possible in all cases, thus violating the contracts of <code>Arrays.sort()</code>.
	 * @param words the array of words to sort
	 */
	public static void sortTopDownRightLeft(ImWord[] words) {
		
		//	sort right-left first
		Arrays.sort(words, Collections.reverseOrder(leftRightOrder));
		
		//	sort individual lines left to right
		int lineStart = 0;
		for (int w = 1; w < words.length; w++)
			
			//	this word starts a new line, sort previous one
			if ((words[w].centerX < words[lineStart].bounds.left) && (words[lineStart].centerX > words[w].bounds.right)) {
				Arrays.sort(words, lineStart, w, topDownOrder);
				lineStart = w;
			}
		
		//	sort last line
		Arrays.sort(words, lineStart, words.length, topDownOrder);
	}
	
	/**
	 * Sorts an array of top-down words in right-left-bottom-up order, in two
	 * steps. This facilitates use of this topological order without the need
	 * to define it in a single <code>Comparator</code>, which is not generally
	 * possible in all cases, thus violating the contracts of <code>Arrays.sort()</code>.
	 * @param words the array of words to sort
	 */
	public static void sortRightLeftBottomUp(ImWord[] words) {
		
		//	sort bottom-up first
		Arrays.sort(words, Collections.reverseOrder(topDownOrder));
		
		//	sort individual lines right to left
		int lineStart = 0;
		for (int w = 1; w < words.length; w++)
			
			//	this word starts a new line, sort previous one
			if ((words[w].centerY < words[lineStart].bounds.top) && (words[lineStart].centerY > words[w].bounds.bottom)) {
				Arrays.sort(words, lineStart, w, Collections.reverseOrder(leftRightOrder));
				lineStart = w;
			}
		
		//	sort last line
		Arrays.sort(words, lineStart, words.length, Collections.reverseOrder(leftRightOrder));
	}
	
	/**
	 * Sorts a list of words in left-right-top-down order, in two steps. This
	 * facilitates use of this topological order without the need to define it
	 * in a single <code>Comparator</code>, which is not generally possible in
	 * all cases, thus violating the contracts of <code>Arrays.sort()</code>.
	 * @param wordList the List of words to sort
	 */
	public static void sortLeftRightTopDown(List wordList) {
		ImWord[] words = ((ImWord[]) wordList.toArray(new ImWord[wordList.size()]));
		sortLeftRightTopDown(words);
		ListIterator wli = wordList.listIterator();
		for (int w = 0; w < words.length; w++) {
		    wli.next();
		    wli.set(words[w]);
		}
	}
	
	/**
	 * Prompt the user for a type for an Image Markup object (usually an
	 * annotation or region). If this method returns a non-null value, the
	 * returned value is a valid object type and can be used without further
	 * checks.
	 * @param tite the title for the prompt dialog
	 * @param text the label text for the prompt dialog
	 * @param existingTypes the existing object types, for selection
	 * @param existingType the current type of the object (may be null)
	 * @param allowInput allow manual input of a non-existing type?
	 * @return the type the user provided or selected
	 */
	public static String promptForObjectType(String title, String text, String[] existingTypes, String existingType, boolean allowInput) {
		StringSelectorPanel ssp = new StringSelectorPanel(title);
		StringSelectorLine ssl = ssp.addSelector(text, existingTypes, existingType, allowInput);
		if (ssp.prompt(DialogFactory.getTopWindow()) != JOptionPane.OK_OPTION)
			return null;
		return ssl.getSelectedTypeOrName(true);
	}
	
	/**
	 * Prompt the user for a type to change on an Image Markup object (usually
	 * an annotation or region). If this method returns a non-null value, the
	 * returned pair of values are valid object types and can be used without
	 * further checks. On top of this, the types are not equal.
	 * @param tite the title for the prompt dialog
	 * @param textOld the label text for the existing part of the prompt dialog
	 * @param textNew the label text for the new part of the prompt dialog
	 * @param existingTypes the existing object types, for selection
	 * @param existingType the current type of the object (may be null)
	 * @param allowInput allow manual input of a non-existing type?
	 * @return the type the user provided or selected
	 */
	public static StringPair promptForObjectTypeChange(String title, String textOld, String textNew, String[] existingTypes, String existingType, boolean allowInput) {
		StringSelectorPanel ssp = new StringSelectorPanel(title);
		final StringSelectorLine sslOld = ssp.addSelector(textOld, existingTypes, existingType, false);
		final StringSelectorLine sslNew = ssp.addSelector(textNew, existingTypes, existingType, true);
		sslOld.selector.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent ie) {
				String oldType = sslOld.getSelectedString();
				sslNew.selector.setSelectedItem(oldType);
			}
		});
		if (ssp.prompt(DialogFactory.getTopWindow()) != JOptionPane.OK_OPTION)
			return null;
		String typeOld = sslOld.getSelectedTypeOrName(false);
		String typeNew = sslNew.getSelectedTypeOrName(true);
		if ((typeOld == null) || (typeNew == null) || typeOld.equals(typeNew))
			return null;
		return new StringPair(typeOld, typeNew);
	}
	
	/**
	 * Container for a pair of strings, for renaming objects, etc.
	 * 
	 * @author sautter
	 */
	public static class StringPair {
		public final String strOld;
		public final String strNew;
		StringPair(String strOld, String strNew) {
			this.strOld = strOld;
			this.strNew = strNew;
		}
	}
	
	/**
	 * A panel with one or more pairs of a label and a combo box of strings,
	 * which can be editable or not. This class is useful for prompting users
	 * for selection or input of (groups of) strings.
	 * 
	 * @author sautter
	 */
	public static class StringSelectorPanel extends JPanel {
		private String title;
		private ArrayList selectors = new ArrayList(1);
		private JPanel selectorPanel = new JPanel(new GridBagLayout(), true);
		private GridBagConstraints gbc = new GridBagConstraints();
		
		/** Constructor
		 * @param title the title to use for prompt dialogs
		 */
		public StringSelectorPanel(String title) {
			super(new BorderLayout(), true);
			this.title = title;
			this.add(this.selectorPanel, BorderLayout.NORTH);
			this.gbc.fill = GridBagConstraints.HORIZONTAL;
			this.gbc.weighty = 0;
			this.gbc.insets.left = 3;
			this.gbc.insets.right = 3;
			this.gbc.insets.top = 1;
			this.gbc.insets.bottom = 1;
		}
		
		/**
		 * Add a string selector line to the panel. This method returns the
		 * newly added string selector line for further configuration, e.g.
		 * adding listeners to the selector combo box.
		 * @param label the label string
		 * @param selectable the selectable strings
		 * @param selected the initially selected string
		 * @param allowInput allow typing in a string beside selecting one?
		 * @return the selector line that was just added
		 */
		public StringSelectorLine addSelector(String label, String[] selectable, String selected, boolean allowInput) {
			StringSelectorLine ssl = new StringSelectorLine(label, selectable, selected, allowInput);
			this.gbc.gridy = this.selectors.size();
			this.gbc.gridx = 0;
			this.gbc.weightx = 1;
			this.selectorPanel.add(ssl.label, this.gbc.clone());
			this.gbc.gridx = 1;
			this.gbc.weightx = 0;
			this.selectorPanel.add(ssl.selector, this.gbc.clone());
			this.selectors.add(ssl);
			return ssl;
		}
		
		/**
		 * Retrieve the index-th selector line as a whole
		 * @param index the index of the selector line
		 * @return the selector line at the argument index
		 */
		public StringSelectorLine selectorAt(int index) {
			return ((StringSelectorLine) this.selectors.get(index));
		}
		
		/**
		 * Retrieve the index-th string.
		 * @param index the index of the selector line to get the string from
		 * @return the string at the argument index
		 */
		public String stringAt(int index) {
			return ((StringSelectorLine) this.selectors.get(index)).getSelectedString();
		}
		
		/**
		 * Retrieve the index-th string as as an object type or attribute name.
		 * This method performs a respective validity check, and returns null
		 * if the string fails the test.
		 * @param index the index of the selector line to get the string from
		 * @param showError show an error message if the test fails?
		 * @return the index-th string as a type or name
		 */
		public String typeOrNameAt(int index, boolean showError) {
			return ((StringSelectorLine) this.selectors.get(index)).getSelectedTypeOrName(showError);
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.JComponent#getPreferredSize()
		 */
		public Dimension getPreferredSize() {
			return new Dimension(400, ((this.selectors.size() * 23) + ((this.selectors.size() - 1) * 2)));
		}
		
		/**
		 * Prompt the user with this string selector panel. This method simply
		 * displays the panel via <code>JOptionPane.showConfirmDialog()</code>.
		 * The returned value is either <code>JOptionPane.OK_OPTION</code> or
		 * <code>JOptionPane.CANCEL_OPTION</code>. This method is sensible to
		 * use only if the string selector panel is not embedded in another
		 * JComponent.
		 * @param parent the component to center upon
		 * @return an indicator for which button the user closed the prompt with
		 */
		public int prompt(Component parent) {
			return DialogFactory.confirm(this, this.title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		}
	}
	
	/**
	 * A panel with a single pair of a label and a combo box of strings, which
	 * can be editable or not. This class is useful for prompting users for
	 * selection or input of a string.
	 * 
	 * @author sautter
	 */
	public static class StringSelectorLine {
		
		/** the label explaining the selector */
		public final JLabel label;
		
		/** the selector combo box */
		public final JComboBox selector;
		
		/**
		 * Constructor
		 * @param label the label string
		 * @param selectable the selectable strings
		 * @param selected the initially selected string
		 * @param allowInput allow typing in a string beside selecting one?
		 */
		StringSelectorLine(String label, String[] selectable, String selected, boolean allowInput) {
			this.label = new JLabel(label);
			this.selector = new JComboBox(selectable);
			this.selector.setEditable(allowInput);
			if (selected != null)
				this.selector.setSelectedItem(selected);
			else if (selectable.length != 0)
				this.selector.setSelectedIndex(0);
		}
		
		/**
		 * Retrieve the string that was selected or typed in.
		 * @return the string
		 */
		public String getSelectedString() {
			Object strObj = this.selector.getSelectedItem();
			if (strObj == null)
				return null;
			String str = strObj.toString().trim();
			return ((str.length() == 0) ? null : str);
		}
		
		/**
		 * Retrieve the string that was selected or typed in, but retrieve it
		 * as an object type or attribute name. This method performs a
		 * respective validity check, and returns null if the string fails the
		 * test.
		 * @param showError show an error message if the test fails?
		 * @return the string as a type or name
		 */
		public String getSelectedTypeOrName(boolean showError) {
			String typeOrName = this.getSelectedString();
			if (AnnotationUtils.isValidAnnotationType(typeOrName))
				return typeOrName;
			if (showError)
				DialogFactory.alert(("'" + typeOrName + "' is not a valid type or name."), "Invalid Type Or Name", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		
		/**
		 * Replace the selectable strings with new ones. This method is
		 * intended for dynamically reacting on updates to other selectors, not
		 * for reuse.
		 * @param selectable the selectable strings
		 * @param selected the initially selected string
		 * @param allowInput allow typing in a string beside selecting one?
		 */
		public void setSelectableStrings(String[] selectable, String selected, boolean allowInput) {
			this.selector.setModel(new DefaultComboBoxModel(selectable));
			this.selector.setEditable(allowInput);
			if (selected != null)
				this.selector.setSelectedItem(selected);
			else if (selectable.length != 0)
				this.selector.setSelectedIndex(0);
		}
	}
	
	/**
	 * Manage of copy operations as first part of copy &amp; paste.
	 * 
	 * @author sautter
	 */
	public static interface CopyManager {
		
		/**
		 * Copy a data string as the first part of a copy &amp; paste operation.
		 * This default implementation simply copies the data to the system
		 * clipboard. Sub classes may overwrite this method to take other measures.
		 * @param data the data to copy
		 */
		public abstract void copy(Transferable data);
	}
	
	/**
	 * Retrieve the current copy manager.
	 * @return the current copy manager
	 */
	public static CopyManager getCopyManager() {
		return copyManager;
	}
	
	/**
	 * Set the copy manager to use for copying data. Setting the copy manager
	 * to <code>null</code> reverts to the default behavior.
	 * @param cm the copy manager to set.
	 */
	public static void setCopyManager(CopyManager cm) {
		copyManager = cm;
	}
	
	private static CopyManager copyManager = null;
	
	/**
	 * Copy a data string as the first part of a copy &amp; paste operation.
	 * If no <code>CopyManager</code> is registered, this method copies the
	 * data to the system clipboard. If a <code>CopyManager</code> is
	 * registered, it is used.
	 * @param data the data to copy
	 */
	public static void copy(Transferable data) {
		if (copyManager == null)
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, null);
		else copyManager.copy(data);
	}
	
	/**
	 * Order a series of words as a separate logical text stream. The words in
	 * the argument array need not belong to an individual logical text stream,
	 * nor need they be single chunks of the logical text streams involved. The
	 * predecessor of the first word is set to the the last predecessor of any
	 * word from the array that is not contained in the array proper, the
	 * successor of the last word is set to the first successor of any word
	 * from the array that is not contained in the array proper.
	 * @param words the words to make a text stream
	 * @param textDirection the text direction to sort the words in
	 */
	public static void orderStream(ImWord[] words, String textDirection) {
		
		//	sort words (if sorting makes sense)
		if (words.length < 2) {}
		else if ((textDirection == null) || ImWord.TEXT_DIRECTION_LEFT_RIGHT.equals(textDirection))
			orderStream(words, leftRightTopDownOrder);
		else if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection))
			orderStream(words, bottomUpLeftRightOrder);
		else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
			orderStream(words, topDownRightLeftOrder);
		else if (ImWord.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(textDirection))
			orderStream(words, rightLeftBottomUpOrder);
		else throw new IllegalArgumentException("Inavlid text direction '" + textDirection + "'");
		
		//	set text direction attributes
		for (int w = 0; w < words.length; w++) {
			if ((textDirection == null) || ImWord.TEXT_DIRECTION_LEFT_RIGHT.equals(textDirection))
				words[w].removeAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE);
			else words[w].setAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, textDirection);
		}
	}
	
	/**
	 * Order a series of words as a separate logical text stream. The words in
	 * the argument array need not belong to an individual logical text stream,
	 * nor need they be single chunks of the logical text streams involved. The
	 * predecessor of the first word is set to the the last predecessor of any
	 * word from the array that is not contained in the array proper, the
	 * successor of the last word is set to the first successor of any word
	 * from the array that is not contained in the array proper.
	 * @param words the words to make a text stream
	 * @param wordOrder the word order to apply
	 */
	public static void orderStream(ImWord[] words, Comparator wordOrder) {
		
		//	anything to work with?
		if (words.length < 2)
			return;
		
		//	index words from array
		HashSet wordIDs = new HashSet();
		CountingSet textStreamIdCounts = new CountingSet();
		for (int w = 0; w < words.length; w++) {
			wordIDs.add(words[w].getLocalID());
			textStreamIdCounts.add(words[w].getTextStreamId());
		}
		
		//	collect predecessors and successors (in text stream order, preserving external ordering)
		Arrays.sort(words, textStreamOrder);
		HashMap predecessorsByTextStreamId = new HashMap();
		HashMap successorsByTextStreamId = new HashMap();
		for (int w = 0; w < words.length; w++) {
			ImWord pWord = words[w].getPreviousWord();
			if ((pWord != null) && !wordIDs.contains(pWord.getLocalID())) {
				System.out.println("External predecessor '" + pWord.getString() + "' (page " + pWord.pageId + " at " + pWord.bounds + ") from '" + words[w].getString() + "' (page " + words[w].pageId + " at " + words[w].bounds + "), in text stream '" + pWord.getTextStreamId() + "'");
				predecessorsByTextStreamId.put(pWord.getTextStreamId(), pWord); // we want the LAST predecessor from each text stream
			}
			ImWord nWord = words[w].getNextWord();
			if ((nWord != null) && !wordIDs.contains(nWord.getLocalID())) {
				System.out.println("External successor '" + nWord.getString() + "' (page " + nWord.pageId + " at " + nWord.bounds + ") from '" + words[w].getString() + "' (page " + words[w].pageId + " at " + words[w].bounds + "), in text stream '" + nWord.getTextStreamId() + "'");
				if (!successorsByTextStreamId.containsKey(nWord.getTextStreamId()))
					successorsByTextStreamId.put(nWord.getTextStreamId(), nWord); // we want the FIRST successor from each text stream
			}
		}
		
		//	cut (connected sequences of) argument words out of surrounding text streams
		for (int w = 0; w < words.length; w++) {
			ImWord pWord = words[w].getPreviousWord();
			if ((pWord != null) && wordIDs.contains(pWord.getLocalID()))
				continue;
			for (int lw = w; lw < words.length; lw++) {
				ImWord nWord = words[lw].getNextWord();
				if ((nWord != null) && wordIDs.contains(nWord.getLocalID()))
					continue;
				if (pWord != null)
					pWord.setNextWord(nWord);
				else if (nWord != null)
					nWord.setPreviousWord(pWord);
				w = lw; // w loop increment moves to next word
				break;
			}
		}
		
		//	order words (use dedicated sort method for left-right-top-down)
		if (wordOrder == leftRightTopDownOrder)
			sortLeftRightTopDown(words);
		else if (wordOrder == bottomUpLeftRightOrder)
			sortBottomUpLeftRight(words);
		else if (wordOrder == topDownRightLeftOrder)
			sortTopDownRightLeft(words);
		else if (wordOrder == rightLeftBottomUpOrder)
			sortRightLeftBottomUp(words);
		else Arrays.sort(words, wordOrder);
		
		//	chain words together
		for (int w = 1; w < words.length; w++) {
			
			//	anything to do here?
			if (words[w].getPreviousWord() == words[w-1])
				continue;
			
			//	words from different text streams, simply chain them, as this cannot produce a cycle
			if (!words[w].getTextStreamId().equals(words[w-1].getTextStreamId())) {
				words[w].setPreviousWord(words[w-1]);
				continue;
			}
			
			//	words in appropriate order, simply chain them, as this cannot produce a cycle
			if (words[w].getTextStreamPos() > words[w-1].getTextStreamPos()) {
				words[w].setPreviousWord(words[w-1]);
				continue;
			}
			
			//	cut off stream after word to avoid cycles before connecting them
			words[w].setNextWord(null);
			words[w].setPreviousWord(words[w-1]);
		}
		
		//	anything to connect to?
		if (predecessorsByTextStreamId.isEmpty() && successorsByTextStreamId.isEmpty())
			return;
		
		//	get predecessor and successor for most frequent text stream ID
		ImWord predecessor = null;
		ImWord successor = null;
//		while (textStreamIdCounts.size() != 0) {
//			String textStreamId = ((String) textStreamIdCounts.max());
//			System.out.println("Seeking external predecessor and successor pair from text stream '" + textStreamId + "'");
//			ImWord pWord = ((ImWord) predecessorsByTextStreamId.get(textStreamId));
//			if ((pWord != null) && (pWord.getNextWord() != null) /* TODOne this MIGHT be null at end of text stream */) {
//				predecessor = pWord;
//				System.out.println(" ==> found predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ")");
//				successor = pWord.getNextWord();
//				System.out.println(" ==> found connected successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ")");
//				break;
//			}
//			ImWord sWord = ((ImWord) successorsByTextStreamId.get(textStreamId));
//			if ((sWord != null) && (sWord.getPreviousWord() != null) /* TODOne this MIGHT be null at start of text stream*/) {
//				successor = sWord;
//				System.out.println(" ==> found successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ")");
//				predecessor = sWord.getPreviousWord();
//				System.out.println(" ==> found connected predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ")");
//				break;
//			}
//			textStreamIdCounts.removeAll(textStreamId);
//		}
		ArrayList psTextStreamIDs = new ArrayList(textStreamIdCounts);
		if (1 < psTextStreamIDs.size())
			Collections.sort(psTextStreamIDs, textStreamIdCounts.getDecreasingCountOrder());
		if ((predecessor == null) || (successor == null)) // seek boundary word pairs from same text stream first
			for (int i = 0; i < psTextStreamIDs.size(); i++) {
				String textStreamId = ((String) psTextStreamIDs.get(i));
				System.out.println("Seeking external predecessor and successor pair from text stream '" + textStreamId + "'");
				ImWord pWord = ((ImWord) predecessorsByTextStreamId.get(textStreamId));
				if ((pWord != null) && (pWord.getNextWord() != null)) {
					predecessor = pWord;
					System.out.println(" ==> found predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ")");
					successor = pWord.getNextWord();
					System.out.println(" ==> found connected successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ")");
					break;
				}
				ImWord sWord = ((ImWord) successorsByTextStreamId.get(textStreamId));
				if ((sWord != null) && (sWord.getPreviousWord() != null)) {
					successor = sWord;
					System.out.println(" ==> found successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ")");
					predecessor = sWord.getPreviousWord();
					System.out.println(" ==> found connected predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ")");
					break;
				}
			}
		if ((predecessor == null) || (successor == null)) // fall back to individual boundary words if pair not found
			for (int i = 0; i < psTextStreamIDs.size(); i++) {
				String textStreamId = ((String) psTextStreamIDs.get(i));
				System.out.println("Seeking individual external predecessor and successor from text stream '" + textStreamId + "'");
				ImWord pWord = ((ImWord) predecessorsByTextStreamId.get(textStreamId));
				if ((pWord != null) && (predecessor == null)) {
					predecessor = pWord;
					System.out.println(" ==> found predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ")");
				}
				ImWord sWord = ((ImWord) successorsByTextStreamId.get(textStreamId));
				if ((sWord != null) && (successor == null)) {
					successor = sWord;
					System.out.println(" ==> found successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ")");
				}
				if ((predecessor != null) && (successor != null))
					break;
			}
		
		//	chain text streams
		if ((predecessor != null) && (words[0].getPreviousWord() != predecessor)) {
			System.out.println("Setting external predecessor '" + predecessor.getString() + "' (page " + predecessor.pageId + " at " + predecessor.bounds + ") for '" + words[0].getString() + "' (page " + words[0].pageId + " at " + words[0].bounds + ")");
			words[0].setPreviousWord(predecessor);
		}
		if ((successor != null) && (words[words.length-1].getNextWord() != successor)) {
			System.out.println("Setting external successor '" + successor.getString() + "' (page " + successor.pageId + " at " + successor.bounds + ") for '" + words[words.length-1].getString() + "' (page " + words[words.length-1].pageId + " at " + words[words.length-1].bounds + ")");
			words[words.length-1].setNextWord(successor);
		}
	}
	
	/**
	 * Make a series of words a separate logical text stream. The words in the
	 * argument array need not belong to an individual logical text stream, nor
	 * need they be single chunks of the logical text streams involved. If the
	 * <code>sType</code> argument is non-null, the type of the newly created
	 * logical text stream is set to this type. If the <code>aType</code>
	 * argument is non-null, an annotation with that type is added to mark the
	 * newly created logical text stream. Any annotation crossing into or out
	 * of (but not over) the argument words will be truncated at at their last
	 * word before the argument ones, or the first word after them.
	 * @param words the words to make a text stream
	 * @param sType the text stream type
	 * @param aType the type annotation to annotate the text stream with
	 * @return the annotation marking the newly created text stream, if any
	 */
	public static ImAnnotation makeStream(ImWord[] words, String sType, String aType) {
		return makeStream(words, sType, aType, true);
	}
	
	/**
	 * Make a series of words a separate logical text stream. The words in the
	 * argument array need not belong to an individual logical text stream, nor
	 * need they be single chunks of the logical text streams involved. If the
	 * <code>sType</code> argument is non-null, the type of the newly created
	 * logical text stream is set to this type. If the <code>aType</code>
	 * argument is non-null, an annotation with that type is added to mark the
	 * newly created logical text stream. If the <code>cutAnnots</code> argument
	 * is set to true, any annotation crossing into or out of (but not over)
	 * the argument words will be truncated at at their last word before the
	 * argument ones, or the first word after them.
	 * @param words the words to make a text stream
	 * @param sType the text stream type
	 * @param aType the type annotation to annotate the text stream with
	 * @param cutAnnots cut annotation crossing onto or out of the argument
	 *            words?
	 * @return the annotation marking the newly created text stream, if any
	 */
	public static ImAnnotation makeStream(ImWord[] words, String sType, String aType, boolean cutAnnots) {
		
		//	anything to work with?
		if (words.length == 0)
			return null;
		
		//	order words
		Arrays.sort(words, textStreamOrder);
		
		//	cut coherent text stream chunks out of their streams and collect them
		LinkedList textStreamParts = new LinkedList();
		ImWord tspStart = words[0];
		ImWord tspEnd = words[0];
		for (int w = 1; w < words.length; w++) {
			
			//	text stream chunk continues
			if (words[w] == tspEnd.getNextWord()) {
				tspEnd = words[w];
				continue;
			}
			
			//	cut out and store current text stream chunk
			textStreamParts.addLast(cutOutTextStream(tspStart, tspEnd, cutAnnots));
			
			//	start new text stream chunk
			tspStart = words[w];
			tspEnd = words[w];
		}
		
		//	cut out and store last chunk
		textStreamParts.addLast(cutOutTextStream(tspStart, tspEnd, cutAnnots));
		
		//	concatenate chunks
		ImWord tsStart = ((ImWord) textStreamParts.removeFirst());
		ImWord tsEnd = tsStart;
		while (textStreamParts.size() != 0) {
			while (tsEnd.getNextWord() != null)
				tsEnd = tsEnd.getNextWord();
			tsEnd.setNextWord((ImWord) textStreamParts.removeFirst());
		}
		while (tsEnd.getNextWord() != null)
			tsEnd = tsEnd.getNextWord();
		
		//	set text stream type
		if ((sType != null) && (sType.trim().length() != 0))
			tsStart.setTextStreamType(sType);
		
		//	add annotation
		if ((aType != null) && (aType.trim().length() != 0))
			return tsStart.getDocument().addAnnotation(tsStart, tsEnd, aType);
		else return null;
	}
	
	/**
	 * Cut a chunk out of a logical text stream. If the two argument words
	 * belong to different logical text streams, this method does nothing.
	 * Otherwise, it connects the predecessor of <code>first</code> to the
	 * successor of <code>last</code>. Any annotation crossing into or out of
	 * (but not over) the span between the argument words will be truncated at
	 * at their last word before the argument ones, or the first word after
	 * them.
	 * @param first the first word of the chunk
	 * @param last the last word of the chunk
	 * @return the head of the newly created text stream
	 */
	public static ImWord cutOutTextStream(ImWord first, ImWord last) {
		return cutOutTextStream(first, last, true);
	}
	
	/**
	 * Cut a chunk out of a logical text stream. If the two argument words
	 * belong to different logical text streams, this method does nothing.
	 * Otherwise, it connects the predecessor of <code>first</code> to the
	 * successor of <code>last</code>. If the <code>cutAnnots</code> argument
	 * is set to true, any annotation crossing into or out of (but not over)
	 * the span between the argument words will be truncated at at their last
	 * word before the argument ones, or the first word after them.
	 * @param first the first word of the chunk
	 * @param last the last word of the chunk
	 * @param cutAnnots cut annotation crossing onto or out of the argument
	 *            words?
	 * @return the head of the newly created text stream
	 */
	public static ImWord cutOutTextStream(ImWord first, ImWord last, boolean cutAnnots) {
		
		//	check arguments
		if (!first.getTextStreamId().equals(last.getTextStreamId()))
			return null;
		
		//	swap words if in wrong order
		if (0 < textStreamOrder.compare(first, last)) {
			ImWord temp = first;
			first = last;
			last = temp;
		}
		
		//	get predecessor and successor of new text stream
		ImWord oldFirstPrev = first.getPreviousWord();
		ImWord oldLastNext = last.getNextWord();
		
		//	truncate annotations
		ImDocument doc = first.getDocument();
		if (doc != null) {
			
			//	incoming but not outgoing
			if (oldFirstPrev != null) {
				ImAnnotation[] inAnnots = doc.getAnnotationsSpanning(oldFirstPrev, first);
				for (int a = 0; a < inAnnots.length; a++) {
					if ((oldLastNext == null) || (textStreamOrder.compare(inAnnots[a].getLastWord(), oldLastNext) < 0))
						inAnnots[a].setLastWord(oldFirstPrev);
				}
			}
			
			//	outgoing but not incoming
			if (oldLastNext != null) {
				ImAnnotation[] outAnnots = doc.getAnnotationsSpanning(last, oldLastNext);
				for (int a = 0; a < outAnnots.length; a++) {
					if ((oldFirstPrev == null) || (textStreamOrder.compare(oldFirstPrev, outAnnots[a].getFirstWord()) < 0))
						outAnnots[a].setFirstWord(oldLastNext);
				}
			}
		}
		
		//	cut out text stream
		if ((oldFirstPrev != null) && (oldLastNext != null))
			oldFirstPrev.setNextWord(oldLastNext);
		else {
			first.setPreviousWord(null);
			last.setNextWord(null);
		}
		
		//	finally
		return first;
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, boolean ignoreLineBreaks) {
		return getString(firstWord, lastWord, -1, null, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param maxLength the maximum length for the returned string
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, int maxLength, boolean ignoreLineBreaks) {
		return getString(firstWord, lastWord, maxLength, null, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, String textDirection, boolean ignoreLineBreaks) {
		return getString(firstWord, lastWord, -1, textDirection, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param maxLength the maximum length for the returned string
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, int maxLength, String textDirection, boolean ignoreLineBreaks) {
		return getString(firstWord, lastWord, maxLength, textDirection, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(firstWord, lastWord, -1, null, ignoreLineBreaks, minSpaceWidth);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param maxLength the maximum length for the returned string
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, int maxLength, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(firstWord, lastWord, maxLength, null, ignoreLineBreaks, minSpaceWidth);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(firstWord, lastWord, -1, textDirection, ignoreLineBreaks, minSpaceWidth);
	}
	
	/**
	 * Concatenate the text of a part of a logical text stream, from one word
	 * up to another one. The two argument words have to belong to the same
	 * logical text stream for this method to behave in any meaningful way.
	 * @param firstWord the word to start from
	 * @param lastWord the word to stop at
	 * @param maxLength the maximum length for the returned string
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord firstWord, ImWord lastWord, int maxLength, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		
		//	count out text direction if missing
		if (textDirection == null) {
			CountingSet textDirections = new CountingSet();
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				textDirections.add(imw.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
				if (imw == lastWord)
					break;
			}
			textDirection = ((String) textDirections.max());
		}
		
		//	make sure we work on single text stream
		if (!firstWord.getTextStreamId().equals(lastWord.getTextStreamId()))
			return (firstWord.getString() + " " + lastWord.getString());
		
		//	check word order
		if (lastWord.getTextStreamPos() < firstWord.getTextStreamPos())
			return "";
		
		//	do we have to do all he hustle and dance?
		if (maxLength < 0)
			return doGetString(firstWord, lastWord, textDirection, ignoreLineBreaks, minSpaceWidth);
		
		//	count out overall length
		if ((lastWord.getTextStreamPos() - firstWord.getTextStreamPos() + 1) <= maxLength) {
			int annotChars = 0;
			for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
				annotChars += imw.getString().length();
				if (imw == lastWord)
					break;
				if (maxLength < annotChars)
					break; // no need to count any further
			}
			
			//	this one's short enough
			if (annotChars <= maxLength)
				return doGetString(firstWord, lastWord, textDirection, ignoreLineBreaks, minSpaceWidth);
		}
		
		//	get end of head
		ImWord headEnd = firstWord;
		int headChars = 0;
		for (; headEnd != null; headEnd = headEnd.getNextWord()) {
			headChars += headEnd.getString().length();
			if ((maxLength / 2) <= headChars)
				break;
			if (headEnd == lastWord)
				break;
		}
		
		//	get start of tail
		ImWord tailStart = lastWord;
		int tailChars = 0;
		for (; tailStart != null; tailStart = tailStart.getPreviousWord()) {
			tailChars += tailStart.getString().length();
			if ((maxLength / 2) <= tailChars)
				break;
			if (tailStart == firstWord)
				break;
			if (tailStart == headEnd)
				break;
		}
		
		//	met in the middle, use whole string
		if ((headEnd == tailStart) || (headEnd.getNextWord() == tailStart))
			return doGetString(firstWord, lastWord, textDirection, ignoreLineBreaks, minSpaceWidth);
		
		//	give head and tail only if annotation too long
		else {
			StringBuffer string = new StringBuffer();
			appendString(firstWord, headEnd, textDirection, ignoreLineBreaks, minSpaceWidth, string);
			string.append(" ... ");
			appendString(tailStart, lastWord, textDirection, ignoreLineBreaks, minSpaceWidth, string);
			return string.toString();
		}
	}
	
	private static String doGetString(ImWord firstWord, ImWord lastWord, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		StringBuffer string = new StringBuffer();
		appendString(firstWord, lastWord, textDirection, ignoreLineBreaks, minSpaceWidth, string);
		return string.toString();
	}
	private static void appendString(ImWord firstWord, ImWord lastWord, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth, StringBuffer string) {
		for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
			string.append(imw.getString());
			if (imw == lastWord)
				break;
			handleNextWordRelation(imw, string, textDirection, ignoreLineBreaks, minSpaceWidth);
		}
	}
	
	/* TODO when getting string for connected ImWords (relationship "continue"):
	- check words for overlap if accent char adjacent to gap ...
	- ... and use combined char if overlap near letter width (should be OK with equidistant split)
	==> also do that when generating tokens in XML wrapper
	==> also use that when merging words if combined character present in font ...
	==> ... or maybe even extend font if char codes available (combine char images, generate transcript, etc., and adjust word char codes)
	==> also try and do that proactively in PdfParser word merging (maybe even across fonts, especially if accent in other font than surrounding words)
	 */		
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear.
	 * @param words the words to concatenate
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, boolean ignoreLineBreaks) {
		return getString(words, null, null, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear.
	 * @param words the words to concatenate
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, String textDirection, boolean ignoreLineBreaks) {
		return getString(words, null, textDirection, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear. If the argument comparator is not null it will be used to sort
	 * the argument array before concatenation.
	 * @param words the words to concatenate
	 * @param wordOrder the comparator to sort the argument words with
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, Comparator wordOrder, boolean ignoreLineBreaks) {
		return getString(words, wordOrder, null, ignoreLineBreaks, -1);
	}
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear.
	 * @param words the words to concatenate
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(words, null, null, ignoreLineBreaks, minSpaceWidth);
	}
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear.
	 * @param words the words to concatenate
	 * @param textDirection the text direction of the argument words
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(words, null, textDirection, ignoreLineBreaks, minSpaceWidth);
	}
	
	/**
	 * Concatenate the text of a sequence of words, regardless of what logical
	 * text stream they belong to, and regardless of whether or not they form
	 * a coherent sequence in their respective logical text stream. Adjacent
	 * words are treated as separate unless they are directly adjacent in the
	 * same logical text stream, in which case their defined relationship comes
	 * to bear. If the argument comparator is not null it will be used to sort
	 * the argument array before concatenation.
	 * @param words the words to concatenate
	 * @param wordOrder the comparator to sort the argument words with
	 * @param ignoreLineBreaks represent line breaks as simple spaces?
	 * @param minSpaceWidth minimum distance between words to consider a space
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, Comparator wordOrder, boolean ignoreLineBreaks, int minSpaceWidth) {
		return getString(words, wordOrder, null, ignoreLineBreaks, minSpaceWidth);
	}
	
	private static String getString(ImWord[] words, Comparator wordOrder, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		if (textDirection == null) {
			if (wordOrder == leftRightTopDownOrder)
				textDirection = ImWord.TEXT_DIRECTION_LEFT_RIGHT;
			else if (wordOrder == bottomUpLeftRightOrder)
				textDirection = ImWord.TEXT_DIRECTION_BOTTOM_UP;
			else if (wordOrder == topDownRightLeftOrder)
				textDirection = ImWord.TEXT_DIRECTION_TOP_DOWN;
		}
		if (textDirection == null) {
			CountingSet textDirections = new CountingSet();
			for (int w = 0; w < words.length; w++)
				textDirections.add(words[w].getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
			textDirection = ((String) textDirections.max());
		}
		if (wordOrder == leftRightTopDownOrder)
			sortLeftRightTopDown(words);
		else if (wordOrder == bottomUpLeftRightOrder)
			sortBottomUpLeftRight(words);
		else if (wordOrder == topDownRightLeftOrder)
			sortTopDownRightLeft(words);
		else if (wordOrder != null)
			Arrays.sort(words, wordOrder);
		StringBuffer sb = new StringBuffer();
		for (int w = 0; w < words.length; w++) {
			sb.append(words[w].getString());
			if ((w+1) == words.length)
				break;
			if (words[w].getNextWord() != words[w+1])
				sb.append(" ");
			else handleNextWordRelation(words[w], sb, textDirection, ignoreLineBreaks, minSpaceWidth);
		}
		return sb.toString();
/* TODO when getting string for connected ImWords (relationship "continue"):
- check words for overlap if accent char adjacent to gap ...
- ... and use combined char if overlap near letter width (should be OK with equidistant split)
==> also do that when generating tokens in XML wrapper
==> also use that when merging words if combined character present in font ...
==> ... or maybe even extend font if char codes available (combine char images, generate transcript, etc., and adjust word char codes)
==> also try and do that proactively in PdfParser word merging (maybe even across fonts, especially if accent in other font than surrounding words)
 */		
	}
	
	private static void handleNextWordRelation(ImWord imw, StringBuffer sb, String textDirection, boolean ignoreLineBreaks, int minSpaceWidth) {
		if (imw.getNextRelation() == ImWord.NEXT_RELATION_SEPARATE) {
			ImWord nextImw = imw.getNextWord();
			if ((nextImw != null) && areSpaced(imw, nextImw, textDirection, minSpaceWidth, -1) && Gamta.insertSpace(imw.getString(), nextImw.getString()))
				sb.append(" ");
		}
		else if (imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
			sb.append(ignoreLineBreaks ? " " : "\r\n");
		else if ((imw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) && (sb.length() != 0))
			sb.deleteCharAt(sb.length()-1);
	}
	
	/**
	 * Check whether or not a space belongs between two words in a generated
	 * string. This method check for text flow breaks, and also observes text
	 * direction in computing the distance between the two words. This method
	 * defaults to a minimum space width (i.e.), distance between the two
	 * argument words of 15% of their average height (as a stand-in for line
	 * height).
	 * @param word1 the first (usually left) word to check
	 * @param word2 the second (usually right) word to check
	 * @return true if there is to be a space between the two word strings
	 */
	public static boolean areSpaced(ImWord word1, ImWord word2) {
		return areSpaced(word1, word2, -1);
	}
	
	/**
	 * Check whether or not a space belongs between two words in a generated
	 * string. This method check for text flow breaks, and also observes text
	 * direction in computing the distance between the two words. This method
	 * returns true is the distance between the two argument words is at least
	 * the argument minimum space width.
	 * @param word1 the first (usually left) word to check
	 * @param word2 the second (usually right) word to check
	 * @param minSpaceWidth the minimum distance to be regarded as a space
	 * @return true if there is to be a space between the two word strings
	 */
	public static boolean areSpaced(ImWord word1, ImWord word2, int minSpaceWidth) {
		//	we can use the attribute from either one word, as text flow break check ensures they are the same
		String textDirection = ((String) word1.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
		return areSpaced(word1, word2, textDirection, minSpaceWidth, -1);
	}
	
	/**
	 * Check whether or not a space belongs between two words in a generated
	 * string. This method check for text flow breaks, and also observes text
	 * direction in computing the distance between the two words. This method
	 * returns true is the distance between the two argument words is at least
	 * the argument minimum fraction of their average height (as a stand-in for
	 * line height).
	 * @param word1 the first (usually left) word to check
	 * @param word2 the second (usually right) word to check
	 * @param minSpaceFract the minimum distance to be regarded as a space, as
	 *            a fraction of line height
	 * @return true if there is to be a space between the two word strings
	 */
	public static boolean areSpaced(ImWord word1, ImWord word2, float minSpaceFract) {
		//	we can use the attribute from either one word, as text flow break check ensures they are the same
		String textDirection = ((String) word1.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
		return areSpaced(word1, word2, textDirection, -1, minSpaceFract);
	}
	
	private static boolean areSpaced(ImWord word1, ImWord word2, String textDirection, int minSpaceWidth, float minSpaceFract) {
		if (areTextFlowBreak(word1, word2))
			return true;
		int wordDist;
		int wordHeight;
		if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection)) {
			if (word2.bounds.bottom > word1.centerY)
				return true; // second word to the left ... WTF
			wordDist = (word1.bounds.top - word2.bounds.bottom);
			wordHeight = ((word1.bounds.getWidth() + word2.bounds.getWidth()) / 2);
		}
		else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(textDirection)) {
			if (word2.bounds.top < word1.centerY)
				return true; // second word to the left ... WTF
			wordDist = (word2.bounds.top - word1.bounds.bottom);
			wordHeight = ((word1.bounds.getWidth() + word2.bounds.getWidth()) / 2);
		}
		else if (ImWord.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(textDirection)) {
			if (word2.bounds.right > word1.centerX)
				return true; // second word to the left ... WTF
			wordDist = (word1.bounds.left - word2.bounds.right);
			wordHeight = ((word1.bounds.getHeight() + word2.bounds.getHeight()) / 2);
		}
		else {
			if (word2.bounds.left < word1.centerX)
				return true; // second word to the left ... WTF
			wordDist = (word2.bounds.left - word1.bounds.right);
			wordHeight = ((word1.bounds.getHeight() + word2.bounds.getHeight()) / 2);
		}
		if (minSpaceWidth != -1) // we have an external threshold, no need for estimating
			return (minSpaceWidth <= wordDist);
		if (0 < minSpaceFract) // we have an external ratio, no need for estimating
			return ((wordHeight * minSpaceFract) <= wordDist);
//		return (wordHeight <= (wordDist * 5)); // should be OK as an estimate, and with some safety margin, at least for born-digital text (0.25 is smallest defaulting space width)
		return ((wordHeight * 3) <= (wordDist * 20)); // 15% should be OK as an estimate lower bound, and with some safety margin, at least for born-digital text (0.25 is smallest defaulting space width)
	}
	
	/**
	 * Construct the string up to a given word, including all preceding words
	 * connected with relationship 'continue' or 'hyphenated'.
	 * @param word the word to work up to
	 * @return the overall string the argument word lies in
	 */
	public static String getStringUpTo(ImWord word) {
		return getStringAround(word, true, false);
	}
	
	/**
	 * Construct the string from a given word, including all following words
	 * connected with relationship 'continue' or 'hyphenated'.
	 * @param word the word to work from
	 * @return the overall string the argument word lies in
	 */
	public static String getStringFrom(ImWord word) {
		return getStringAround(word, false, true);
	}
	
	/**
	 * Construct the string around a given word, including all preceding and
	 * following words connected with relationship 'continue' or 'hyphenated'.
	 * @param word the word to work around
	 * @return the overall string the argument word lies in
	 */
	public static String getStringAround(ImWord word) {
		return getStringAround(word, true, true);
	}
	
	private static String getStringAround(ImWord word, boolean includePredecessors, boolean includeSuccessors) {
		ImWord start = word;
		while (includePredecessors && (start.getPreviousRelation() == ImWord.NEXT_RELATION_CONTINUE) || (start.getPreviousRelation() == ImWord.NEXT_RELATION_HYPHENATED))
			start = start.getPreviousWord();
		ImWord end = word;
		while (includeSuccessors && (end.getNextRelation() == ImWord.NEXT_RELATION_CONTINUE) || (end.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED))
			end = end.getNextWord();
		return getString(start, end, true);
	}
	
	/**
	 * Check if there is a text flow break right before a given word. This
	 * method returns true if (a) the argument word is the first in a logical
	 * text stream or (b) there is a text flow break between the predecessor of
	 * the argument word and the argument word proper.
	 * @param word the word to check
	 * @return true if there is a text flow break before the argument word
	 * @see de.uka.ipd.idaho.im.util.ImUtils#areTextFlowBreak();
	 */
	public static boolean hasTextFlowBreakBefore(ImWord word) {
		ImWord prevWord = word.getPreviousWord();
		return ((prevWord == null) || areTextFlowBreak(prevWord, word));
	}
	
	/**
	 * Check if there is a text flow break right after a given word. This
	 * method returns true if (a) the argument word is the last in a logical
	 * text stream or (b) there is a text flow break between the argument word
	 * and its successor.
	 * @param word the word to check
	 * @return true if there is a text flow break after the argument word
	 * @see de.uka.ipd.idaho.im.util.ImUtils#areTextFlowBreak();
	 */
	public static boolean hasTextFlowBreakAfter(ImWord word) {
		ImWord nextWord = word.getNextWord();
		return ((nextWord == null) || areTextFlowBreak(word, nextWord));
	}
	
	/**
	 * Check if there is a text flow break between two given words. This method
	 * returns true if (a) the words have different text directions, (b) the
	 * words are on different pages, or (c) neither word has a vertical overlap
	 * of at least half its height with the other. The latter checks both ways
	 * to cover cases of differing font sizes, as e.g. between super- or
	 * subscripts and regular words in a line.
	 * @param word1 the first word to check
	 * @param word2 the second word to check
	 * @return true if there is a text flow break between the argument words
	 */
	public static boolean areTextFlowBreak(ImWord word1, ImWord word2) {
		if (word1.pageId != word2.pageId)
			return true; // different pages
		String textDirection1 = ((String) word1.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
		String textDirection2 = ((String) word2.getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT));
		if (!textDirection1.equals(textDirection2))
			return true; // shift of text orientation
		if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection1)) {
			if (word1.bounds.right <= word2.bounds.left)
				return true; // complete vertical offset, second word below first (e.g. line break)
			if (word2.bounds.right <= word1.bounds.left)
				return true; // complete vertical offset, first word below second (e.g. column break)
			if ((word1.centerX <= word2.bounds.left) && (word1.bounds.right <= word2.centerX))
				return true; // vertical offset with no center inside other word, second word below first (too much offset for tailing subscript)
			if ((word2.centerX <= word1.bounds.left) && (word2.bounds.right <= word1.centerX))
				return true; // vertical offset with no center inside other word, first word below second (too much offset for tailing superscript)
			return false;
		}
		else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(textDirection1)) {
			if (word1.bounds.right <= word2.bounds.left)
				return true; // complete vertical offset, second word above first (e.g. line break)
			if (word2.bounds.right <= word1.bounds.left)
				return true; // complete vertical offset, first word above second (e.g. column break)
			if ((word1.centerX <= word2.bounds.left) && (word1.bounds.right <= word2.centerX))
				return true; // vertical offset with no center inside other word, second word above first (too much offset for tailing subscript)
			if ((word2.centerX <= word1.bounds.left) && (word2.bounds.right <= word1.centerX))
				return true; // vertical offset with no center inside other word, first word above second (too much offset for tailing superscript)
			return false;
		}
		else if (ImWord.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(textDirection1)) {
			if (word1.bounds.bottom <= word2.bounds.top)
				return true; // complete vertical offset, second word above first (e.g. line break)
			if (word2.bounds.bottom <= word1.bounds.top)
				return true; // complete vertical offset, first word above second (e.g. column break)
			if ((word1.centerY <= word2.bounds.top) && (word1.bounds.bottom <= word2.centerY))
				return true; // vertical offset with no center inside other word, second word above first (too much offset for tailing subscript)
			if ((word2.centerY <= word1.bounds.top) && (word2.bounds.bottom <= word1.centerY))
				return true; // vertical offset with no center inside other word, first word above second (too much offset for tailing superscript)
			return false;
		}
		else {
			if (word1.bounds.bottom <= word2.bounds.top)
				return true; // complete vertical offset, second word below first (e.g. line break)
			if (word2.bounds.bottom <= word1.bounds.top)
				return true; // complete vertical offset, first word below second (e.g. column break)
			if ((word1.centerY <= word2.bounds.top) && (word1.bounds.bottom <= word2.centerY))
				return true; // vertical offset with no center inside other word, second word below first (too much offset for tailing subscript)
			if ((word2.centerY <= word1.bounds.top) && (word2.bounds.bottom <= word1.centerY))
				return true; // vertical offset with no center inside other word, first word below second (too much offset for tailing superscript)
			return false;
		}
	}
	
	/**
	 * Check if there is a hyphenation after a given word, judging from
	 * morphological evidence. This method return true if (a) the argument
	 * word is not the last one in tits text stream and (b) the
	 * <code>areHyphenated()</code> method finds a hyphenation between the
	 * argument word and its successor.
	 * @param word the word to check
	 * @return true if there is a hyphenation after the argument word
	 * @see de.uka.ipd.idaho.im.util.ImUtils#areHyphenated();
	 */
	public static boolean isHyphenatedAfter(ImWord word) {
		ImWord nextWord = word.getNextWord();
		return ((nextWord != null) && areHyphenated(word, nextWord));
	}
	
	/**
	 * Check if there is a hyphenation before a given word, judging from
	 * morphological evidence. This method return true if (a) the argument
	 * word is not the first one in tits text stream and (b) the
	 * <code>areHyphenated()</code> method finds a hyphenation between the
	 * predecessor of the argument word and the argument word proper.
	 * @param word the word to check
	 * @return true if there is a hyphenation before the argument word
	 * @see de.uka.ipd.idaho.im.util.ImUtils#areHyphenated();
	 */
	public static boolean isHyphenatedBefore(ImWord word) {
		ImWord prevWord = word.getPreviousWord();
		return ((prevWord != null) && areHyphenated(prevWord, word));
	}
	
	/**
	 * Check if there is a hyphenation between two given words, judging from
	 * morphological evidence. This method return true if (a) both words are
	 * actual words, (b) the first one ends with a hyphen, and (c) the second
	 * one is in lower case and is not a preposition usually found in an
	 * enumeration. This method also considers any connected predecessors of
	 * the first word and any connected successors of the second word.
	 * @param firstWord the first word to check
	 * @param secondWord the second word to check
	 * @return true if there is a hyphenation between the two argument words
	 */
	public static boolean areHyphenated(ImWord firstWord, ImWord secondWord) {
		
		//	get and check first word string, including connected predecessors
		String fWordStr = getStringUpTo(firstWord);
		if ((fWordStr == null) || (fWordStr.length() == 0))
			return false;
		
		//	get and check second word string, including connected successors
		String sWordStr = getStringFrom(secondWord);
		if ((sWordStr == null) || (sWordStr.length() == 0))
			return false;
		
		//	check word strings
		return StringUtils.areHyphenated(fWordStr, sWordStr);
	}
	
	/**
	 * Find potential captions for a given target area (the region marking what
	 * a caption can refer to, e.g. a table of figure) in a page of an Image
	 * Markup document. In particular, this method seeks caption annotations
	 * above and/or below (depending on the respective arguments) the target
	 * region within on inch distance. If target type matching is active, this
	 * method only returns captions starting with 'Tab' if the target region is
	 * a table, and exclude those captions if the target region is not a table. 
	 * @param pages the page to search captions in
	 * @param target the caption target region
	 * @param above search captions above the target region?
	 * @param below search captions below the target region?
	 * @param matchTargetType match caption type and target type?
	 * @return an array holding potential captions for the argument target region
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static ImAnnotation[] findCaptions(ImRegion target, boolean above, boolean below, boolean matchTargetType) {
		return findCaptions(target, above, below, false, false, matchTargetType);
	}
	
	/**
	 * Find potential captions for a given target area (the region marking what
	 * a caption can refer to, e.g. a table of figure) in a page of an Image
	 * Markup document. In particular, this method seeks caption annotations
	 * above and/or below (depending on the respective arguments) the target
	 * region within on inch distance. If target type matching is active, this
	 * method only returns captions starting with 'Tab' if the target region is
	 * a table, and exclude those captions if the target region is not a table. 
	 * @param pages the page to search captions in
	 * @param target the caption target region
	 * @param above search captions above the target region?
	 * @param below search captions below the target region?
	 * @param beside search captions to left and right of the target region?
	 * @param matchTargetType match caption type and target type?
	 * @return an array holding potential captions for the argument target region
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static ImAnnotation[] findCaptions(ImRegion target, boolean above, boolean below, boolean beside, boolean matchTargetType) {
		return findCaptions(target, above, below, beside, false, matchTargetType);
	}
	
	/**
	 * Find potential captions for a given target area (the region marking what
	 * a caption can refer to, e.g. a table or figure) in a page of an Image
	 * Markup document. In particular, this method seeks caption annotations
	 * above and/or below (depending on the respective arguments) the target
	 * region within on inch distance. If target type matching is active, this
	 * method only returns captions starting with 'Tab' if the target region is
	 * a table, and exclude those captions if the target region is not a table. 
	 * @param pages the page to search captions in
	 * @param target the caption target region
	 * @param above search captions above the target region?
	 * @param below search captions below the target region?
	 * @param beside search captions to left and right of the target region?
	 * @param inside search captions inside the target region?
	 * @param matchTargetType match caption type and target type?
	 * @return an array holding potential captions for the argument target region
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static ImAnnotation[] findCaptions(ImRegion target, boolean above, boolean below, boolean beside, boolean inside, boolean matchTargetType) {
		
		//	quick check alignment switches
		if (!above && !below && !beside && !inside)
			return new ImAnnotation[0];
		
		//	get page (might be null for regions not added to document)
		ImPage page = target.getPage();
		if (page == null)
			return new ImAnnotation[0];
		
		//	seek captions through paragraphs
		ArrayList captionList = new ArrayList();
		ImRegion[] paragraphs = page.getRegions(ImRegion.PARAGRAPH_TYPE);
		if (paragraphs.length == 0)
			return new ImAnnotation[0];
		int dpi = page.getImageDPI();
		Arrays.sort(paragraphs, topDownOrder);
		
		//	add captions above target area (only the closest ones, though)
		if (above) {
			ImWord captionStartWord = null;
			for (int p = 0; p < paragraphs.length; p++) {
				
				//	we're definitely below the 'above' target paragraphs
				if (paragraphs[p].bounds.top > target.bounds.top)
					break; // due to top-down sort order, we won't find any better from here onward
				
				//	check spatial match
				if (!isCaptionAboveTargetMatch(paragraphs[p].bounds, target.bounds, dpi))
					continue;
				
				//	check words
				ImWord[] paragraphWords = paragraphs[p].getWords();
				if (!checkCaptionWords(paragraphWords, (matchTargetType ? target.getType() : null)))
					continue;
				
				//	keep start word for getting annotations
				captionStartWord = paragraphWords[0];
			}
			
			//	get annotations for caption start closest above target
			if (captionStartWord != null) {
				ImAnnotation[] captionAnnots = page.getDocument().getAnnotations(captionStartWord, null);
				for (int a = 0; a < captionAnnots.length; a++) {
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()) && !captionAnnots[a].hasAttribute(IN_LINE_OBJECT_MARKER_ATTRIBUTE))
						captionList.add(captionAnnots[a]);
				}
			}
		}
		
		//	add captions below target area (only the closest ones, though)
		if (below)
			for (int p = 0; p < paragraphs.length; p++) {
				
				//	we're definitely above the the 'below' target paragraphs
				if (paragraphs[p].bounds.bottom < target.bounds.bottom)
					continue;
				
				//	check distance (less than an inch)
				if (dpi < (paragraphs[p].bounds.top - target.bounds.bottom))
					break; // due to top-down sort order, we won't find any better from here onward
				
				//	check spatial match
				if (!isCaptionBelowTargetMatch(paragraphs[p].bounds, target.bounds, dpi))
					continue;
				
				//	check words
				ImWord[] paragraphWords = paragraphs[p].getWords();
				if (!checkCaptionWords(paragraphWords, (matchTargetType ? target.getType() : null)))
					continue;
				
				//	get annotations directly here
				ImAnnotation[] captionAnnots = page.getDocument().getAnnotations(paragraphWords[0], null);
				for (int a = 0; a < captionAnnots.length; a++) {
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()) && !captionAnnots[a].hasAttribute(IN_LINE_OBJECT_MARKER_ATTRIBUTE))
						captionList.add(captionAnnots[a]);
				}
				
				//	caption or not, we won't find any better-fitting caption
				break;
			}
		
		//	add captions to left and right of target area (only the closest ones, though)
		if (beside) {
			ImWord captionStartWord = null;
			int minHorizontalDist = Integer.MAX_VALUE;
			for (int p = 0; p < paragraphs.length; p++) {
				
				//	we're definitely above the 'beside' target paragraphs
				if (((paragraphs[p].bounds.top + paragraphs[p].bounds.bottom) / 2) < target.bounds.top)
					continue;
				
				//	we're definitely below the 'beside' target paragraphs
				if (((paragraphs[p].bounds.top + paragraphs[p].bounds.bottom) / 2) > target.bounds.bottom)
					break; // due to top-down sort order, we won't find any better from here onward
				
				//	check spatial match
				if (!isCaptionBesideTargetMatch(paragraphs[p].bounds, target.bounds, dpi))
					continue;
				
				//	make sure to prefer paragraph closest to target area
				int captionOnLeftDist = Math.abs(target.bounds.left - paragraphs[p].bounds.right);
				int captionOnRightDist = Math.abs(paragraphs[p].bounds.left - target.bounds.right);
				int horizontalDist = Math.min(captionOnLeftDist, captionOnRightDist);
				if (minHorizontalDist < horizontalDist)
					continue;
				
				//	check words
				ImWord[] paragraphWords = paragraphs[p].getWords();
				if (!checkCaptionWords(paragraphWords, (matchTargetType ? target.getType() : null)))
					continue;
				
				//	keep start word for getting annotations
				captionStartWord = paragraphWords[0];
				minHorizontalDist = horizontalDist;
			}
			
			//	get annotations for caption start closest above target
			if (captionStartWord != null) {
				ImAnnotation[] captionAnnots = page.getDocument().getAnnotations(captionStartWord, null);
				for (int a = 0; a < captionAnnots.length; a++) {
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()) && !captionAnnots[a].hasAttribute(IN_LINE_OBJECT_MARKER_ATTRIBUTE))
						captionList.add(captionAnnots[a]);
				}
			}
		}
		
		//	add captions inside target area
		if (inside) {
			ImWord captionStartWord = null;
			for (int p = 0; p < paragraphs.length; p++) {
				
				//	this one is not inside the target region
				if (!target.bounds.includes(paragraphs[p].bounds, true))
					continue;
				
				//	check words
				ImWord[] paragraphWords = paragraphs[p].getWords();
				if (!checkCaptionWords(paragraphWords, (matchTargetType ? target.getType() : null)))
					continue;
				
				//	keep start word for getting annotations
				captionStartWord = paragraphWords[0];
			}
			
			//	get annotations for caption start closest above target
			if (captionStartWord != null) {
				ImAnnotation[] captionAnnots = page.getDocument().getAnnotations(captionStartWord, null);
				for (int a = 0; a < captionAnnots.length; a++) {
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()) && !captionAnnots[a].hasAttribute(IN_LINE_OBJECT_MARKER_ATTRIBUTE))
						captionList.add(captionAnnots[a]);
				}
			}
		}
		
		//	finally
		return ((ImAnnotation[]) captionList.toArray(new ImAnnotation[captionList.size()]));
	}
	
	private static boolean checkCaptionWords(ImWord[] paragraphWords, String targetType) {
		if (paragraphWords.length == 0)
			return false;
		if (!ImWord.TEXT_STREAM_TYPE_CAPTION.equals(paragraphWords[0].getTextStreamType()))
			return false;
		Arrays.sort(paragraphWords, textStreamOrder);
		if (paragraphWords[0].getString() == null)
			return false;
		if ((targetType != null) && (ImRegion.TABLE_TYPE.equals(targetType) != paragraphWords[0].getString().toLowerCase().startsWith("tab")))
			return false;
		
		return true;
	}
	
	/**
	 * Compare the positions of a caption and a target area (the bounding box
	 * of what the caption refers to) in a page. In particular, this method
	 * checks if the caption is above above or below the target, with at most
	 * one inch of vertical distance in between, and if the horizontal center
	 * of each of the two arguments lies within the other one.
	 * @param captionBox the caption bounding box
	 * @param targetBox the target area bounding box
	 * @param dpi the resolution of the underlying page image
	 * @return true if caption and target match in terms of relative position
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionAboveTargetMatch(BoundingBox, BoundingBox, int))
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionBelowTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static boolean isCaptionTargetMatch(BoundingBox captionBox, BoundingBox targetBox, int dpi) {
		return isCaptionTargetMatch(captionBox, targetBox, dpi, false);
	}
	
	/**
	 * Compare the positions of a caption and a target area (the bounding box
	 * of what the caption refers to) in a page. In particular, this method
	 * checks if the caption is above above or below the target, with at most
	 * one inch of vertical distance in between, and if the horizontal center
	 * of each of the two arguments lies within the other one.
	 * @param captionBox the caption bounding box
	 * @param targetBox the target area bounding box
	 * @param dpi the resolution of the underlying page image
	 * @param beside allow captions to be on left or right of the target region?
	 * @return true if caption and target match in terms of relative position
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionAboveTargetMatch(BoundingBox, BoundingBox, int))
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionBelowTargetMatch(BoundingBox, BoundingBox, int))
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionBesideTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static boolean isCaptionTargetMatch(BoundingBox captionBox, BoundingBox targetBox, int dpi, boolean beside) {
		return (isCaptionAboveTargetMatch(captionBox, targetBox, dpi) || isCaptionBelowTargetMatch(captionBox, targetBox, dpi) || (beside && isCaptionBesideTargetMatch(captionBox, targetBox, dpi)));
	}
	
	/**
	 * Compare the positions of a caption and a target area (the bounding box
	 * of what the caption refers to) in a page. In particular, this method
	 * checks if the caption is above the target, with at most one inch of
	 * vertical distance in between, and if the horizontal center of each of
	 * the two arguments lies within the other one.
	 * @param captionBox the caption bounding box
	 * @param targetBox the target area bounding box
	 * @param dpi the resolution of the underlying page image
	 * @return true if caption and target match in terms of relative position
	 */
	public static boolean isCaptionAboveTargetMatch(BoundingBox captionBox, BoundingBox targetBox, int dpi) {
		
		//	check vertical alignment (we need to cut this a little slack, as images can be masked to smaller sizes in born-digital PDFs)
		if (targetBox.top < ((captionBox.top + captionBox.bottom) / 2))
			return false;
		
		//	compute widths and overlap
		int captionBoxWidth = (captionBox.right - captionBox.left);
		int targetBoxWidth = (targetBox.right - targetBox.left);
		int overlapWidth = (Math.min(captionBox.right, targetBox.right) - Math.max(captionBox.left, targetBox.left));
		
		//	80% overlap of narrower in wider part should do
		if ((overlapWidth * 5) < (Math.min(captionBoxWidth, targetBoxWidth) * 4))
			return false;
		
		//	check distance (less than an inch)
		return ((targetBox.top - captionBox.bottom) <= dpi);
	}
	
	/**
	 * Compare the positions of a caption and a target area (the bounding box
	 * of what the caption refers to) in a page. In particular, this method
	 * checks if the caption is below the target, with at most one inch of
	 * vertical distance in between, and if the horizontal center of each of
	 * the two arguments lies within the other one.
	 * @param captionBox the caption bounding box
	 * @param targetBox the target area bounding box
	 * @param dpi the resolution of the underlying page image
	 * @return true if caption and target match in terms of relative position
	 */
	public static boolean isCaptionBelowTargetMatch(BoundingBox captionBox, BoundingBox targetBox, int dpi) {
		
		//	check vertical alignment (we need to cut this a little slack, as images can be masked to smaller sizes in born-digital PDFs)
		if (((captionBox.top + captionBox.bottom) / 2) < targetBox.bottom)
			return false;
		
		//	compute widths and overlap
		int captionBoxWidth = (captionBox.right - captionBox.left);
		int targetBoxWidth = (targetBox.right - targetBox.left);
		int overlapWidth = (Math.min(captionBox.right, targetBox.right) - Math.max(captionBox.left, targetBox.left));
		
		//	80% overlap of narrower in wider part should do
		if ((overlapWidth * 5) < (Math.min(captionBoxWidth, targetBoxWidth) * 4))
			return false;
		
		//	check distance (less than an inch)
		return ((captionBox.top - targetBox.bottom) <= dpi);
	}
	
	/**
	 * Compare the positions of a caption and a target area (the bounding box
	 * of what the caption refers to) in a page. In particular, this method
	 * checks if the caption is beside the target, with at most one inch of
	 * horizontal distance in between, and if the vertical center of each of
	 * the two arguments lies within the other one.
	 * @param captionBox the caption bounding box
	 * @param targetBox the target area bounding box
	 * @param dpi the resolution of the underlying page image
	 * @return true if caption and target match in terms of relative position
	 */
	public static boolean isCaptionBesideTargetMatch(BoundingBox captionBox, BoundingBox targetBox, int dpi) {
		
		//	compute heights and overlap
		int captionBoxHeight = (captionBox.bottom - captionBox.top);
		int targetBoxHeight = (targetBox.bottom - targetBox.top);
		int overlapHeight = (Math.min(captionBox.bottom, targetBox.bottom) - Math.max(captionBox.top, targetBox.top));
		
		//	80% overlap of narrower in wider part should do
		if ((overlapHeight * 5) < (Math.min(captionBoxHeight, targetBoxHeight) * 4))
			return false;
		
		//	check distance (less than an inch)
		int captionOnLeftDist = Math.abs(targetBox.left - captionBox.right);
		int captionOnRightDist = Math.abs(captionBox.left - targetBox.right);
		return (Math.min(captionOnLeftDist, captionOnRightDist) <= dpi);
	}
	
	/**
	 * Link a caption to its target region. In particular, this method sets the
	 * <code>targetBox</code> and <code>targetPageId</code> attributes of the
	 * argument caption.
	 * @param caption the caption to link
	 * @param target the target region to link the caption to
	 */
	public static void linkCaptionTarget(ImAnnotation caption, ImRegion target) {
		caption.setAttribute(CAPTION_TARGET_BOX_ATTRIBUTE, target.bounds.toString());
		caption.setAttribute(CAPTION_TARGET_PAGE_ID_ATTRIBUTE, ("" + target.pageId));
	}
	
	/**
	 * Retrieve the target region linked to a caption. If no target region is
	 * found in the linked page for the exact linked bounding box, this method
	 * resorts to fuzzy matching.
	 * @param caption the caption whose target to retrieve
	 * @return the caption target region
	 */
	public static ImRegion getCaptionTarget(ImAnnotation caption) {
		return getCaptionTarget(caption, null);
	}
	
	/**
	 * Retrieve the target region linked to a caption. If no target region is
	 * found in the linked page for the exact linked bounding box, this method
	 * resorts to fuzzy matching.
	 * @param caption the caption whose target to retrieve
	 * @param targetType the type of the caption target
	 * @return the caption target region
	 */
	public static ImRegion getCaptionTarget(ImAnnotation caption, String targetType) {
		ImDocument ctDoc = caption.getDocument();
		if (ctDoc == null)
			return null;
		BoundingBox ctBox;
		int ctPageId;
		try {
			String ctBoxStr = ((String) caption.getAttribute(CAPTION_TARGET_BOX_ATTRIBUTE));
			if (ctBoxStr == null)
				return null;
			ctBox = BoundingBox.parse(ctBoxStr);
			String ctPageIdStr = ((String) caption.getAttribute(CAPTION_TARGET_PAGE_ID_ATTRIBUTE));
			if (ctPageIdStr == null)
				return null;
			ctPageId = Integer.parseInt(ctPageIdStr);
		}
		catch (IllegalArgumentException iae) {
			iae.printStackTrace(System.out);
			return null;
		}
		ImPage ctPage = ctDoc.getPage(ctPageId);
		if (ctPage == null)
			return null;
		ImRegion[] ctRegions = ctPage.getRegionsIncluding(targetType, ctBox, true);
		float bestMatchScore = 0;
		ImRegion bestMatchRegion = null;
		for (int r = 0; r < ctRegions.length; r++) {
			if (targetType != null) {} // we've already done the type filtering
			else if (ImRegion.IMAGE_TYPE.equals(ctRegions[r].getType())) {}
			else if (ImRegion.GRAPHICS_TYPE.equals(ctRegions[r].getType())) {}
			else if (ImRegion.TABLE_TYPE.equals(ctRegions[r].getType())) {}
			else continue; // not a suitable type for a caption target TODO amend list of suitable types as need arises
			if (ctBox.equals(ctRegions[r].bounds))
				return ctRegions[r]; // exact match, we're done here
			BoundingBox overlapBox = ctBox.intersect(ctRegions[r].bounds);
			if (overlapBox == null)
				continue;
			float overlap = overlapBox.getArea();
			float matchScore = ((overlap * overlap) / ctRegions[r].bounds.getArea()); // factors in both size and fraction overlapping with target box
			if (bestMatchScore < matchScore) {
				bestMatchScore = matchScore;
				bestMatchRegion = ctRegions[r];
			}
		}
		return bestMatchRegion;
	}
//	
//	/**
//	 * Details on the origin of an Image Markup Document.
//	 * 
//	 * @author sautter
//	 */
//	public static class DocumentOrigin {
//		
//		/** text origin indicating document text that was born-digital */
//		public static final char TEXT_ORIGIN_BORN_DIGITAL = 'B';
//		
//		/** text origin indicating document text that was scanned and then replaced by a digital rendition */
//		public static final char TEXT_ORIGIN_DIGITIZED_OCR = 'D';
//		
//		/** text origin indicating document text that was scanned and then replaced by a vectorized rendition (e.g. DjVu encoding) */
//		public static final char TEXT_ORIGIN_VECTORIZED = 'V';
//		
//		/** text origin indicating document text that was created by OCR */
//		public static final char TEXT_ORIGIN_OCR = 'O';
//		
//		/** indicates if the image markup document displayed in this viewer panel is born-digital page backgrounds */
//		public final boolean documentPagesBornDigital;
//		
//		/** indicates how the text of the image markup document displayed in this viewer panel was generated */
//		public final char documentTextOrigin;
//		
//		DocumentOrigin(boolean pbd, char dto) {
//			this.documentPagesBornDigital = pbd;
//			this.documentTextOrigin = dto;
//		}
//	}
	
	/** text origin indicating document text that renders the original state of pages */
	public static final int DOCUMENT_TEXT_SHOWS_ORIGINAL_STATE = 0x00000001;
	
	/** text origin indicating document text that is rendered using a 'normal' digital font */
	public static final int DOCUMENT_TEXT_RENDERED_WITH_NORMAL_FONT = 0x00000002;
	
	/** text origin indicating document text that was created by OCR */
	public static final int DOCUMENT_TEXT_ORIGIN_OCR = 0x00000000;
	
	/** document text origin indicating document text that was scanned and then replaced by a vectorized rendition (e.g. DjVu encoding) */
	public static final int DOCUMENT_TEXT_ORIGIN_VECTORIZED_OCR = (DOCUMENT_TEXT_ORIGIN_OCR | DOCUMENT_TEXT_SHOWS_ORIGINAL_STATE);
	
	/** document text origin indicating document text that was scanned and then replaced by a digital rendition */
	public static final int DOCUMENT_TEXT_ORIGIN_DIGITIZED_OCR = (DOCUMENT_TEXT_ORIGIN_OCR | DOCUMENT_TEXT_RENDERED_WITH_NORMAL_FONT);
	
	/** document text origin indicating document text that was born-digital */
	public static final int DOCUMENT_TEXT_ORIGIN_BORN_DIGITAL = (DOCUMENT_TEXT_RENDERED_WITH_NORMAL_FONT | DOCUMENT_TEXT_SHOWS_ORIGINAL_STATE);
	
	/** document page image origin indicating page background that was scanned from an image */
	public static final int DOCUMENT_PAGES_SCANNED = 0x00000000;
	
	/** document page image origin indicating page background that was rendered from digital graphics */
	public static final int DOCUMENT_PAGES_RENDERED = 0x00010000;
	
	/**
	 * Check whether or not a bit vector document origin indicates born-digital
	 * page backgrounds.
	 * @param docOrigin the document origin to check
	 * @return true if the argument document origin indicates born-digital pages
	 */
	public static boolean arePagesBordDigital(int docOrigin) {
		return ((docOrigin & DOCUMENT_PAGES_RENDERED) != 0);
	}
	
	/**
	 * Check whether or not a bit vector document origin indicates document
	 * text rendered via 'normal' fonts.
	 * @param docOrigin the document origin to check
	 * @return true if the argument document origin indicates use of 'normal'
	 *            fonts for the document text
	 */
	public static boolean textUsesNormalFonts(int docOrigin) {
		return ((docOrigin & DOCUMENT_TEXT_RENDERED_WITH_NORMAL_FONT) != 0);
	}
	
	/**
	 * Check whether or not a bit vector document origin indicates document
	 * text representing the original state of the source document.
	 * @param docOrigin the document origin to check
	 * @return true if the argument document origin indicates document text in
	 *            its original state
	 */
	public static boolean textShowsOriginalState(int docOrigin) {
		return ((docOrigin & DOCUMENT_TEXT_SHOWS_ORIGINAL_STATE) != 0);
	}
	
	/**
	 * Assess the origin of a document, e.g. whether its pages were scanned or
	 * rendered digitally.
	 * @param doc the document to assess
	 * @return the document origin
	 */
	public static int assessDocumentOrigin(ImDocument doc) {
		return assessDocumentOrigin(doc.getPages());
	}
	
	/**
	 * Assess the origin of a document, e.g. whether its pages were scanned or
	 * rendered digitally.
	 * @param pages the pages of the document to assess
	 * @return the document origin
	 */
	public static int assessDocumentOrigin(ImPage[] pages) {
		
		//	assess pages, words, and supplements
		int pageCount = 0;
		int scanCount = 0;
		int wordCount = 0;
		int fWordCount = 0;
		int vfWordCount = 0;
		for (int p = 0; p < pages.length; p++) {
			if (pages[p] == null)
				continue;
			pageCount++;
			ImSupplement[] pageSupplements = pages[p].getSupplements();
			for (int s = 0; s < pageSupplements.length; s++) {
				if (pageSupplements[s] instanceof ImSupplement.Scan)
					scanCount++;
			}
			ImWord[] pageWords = pages[p].getWords();
			int minWordLeft = pages[p].bounds.right;
			int maxWordRight = pages[p].bounds.left;
			int minWordTop = pages[p].bounds.bottom;
			int maxWordBottom = pages[p].bounds.top;
			for (int w = 0; w < pageWords.length; w++) {
				wordCount++;
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
		}
		
		//	evaluate what we've found
		int docOrigin = 0;
//		boolean documentPagesBornDigital = ((scanCount * 3) < (pageCount * 2)); // fewer than two thirds or pages have scans
		if ((scanCount * 3) < (pageCount * 2)) // fewer than two thirds or pages have scans
			docOrigin |= DOCUMENT_PAGES_RENDERED;
//		char documentTextOrigin;
		if ((fWordCount * 3) < (wordCount * 2)) // fewer than two thirds of words bound to font at all
//			documentTextOrigin = DocumentOrigin.TEXT_ORIGIN_OCR;
			docOrigin |= DOCUMENT_TEXT_ORIGIN_OCR;
		else if ((fWordCount * 2) < (vfWordCount * 3)) // more than two thirds of font-bound words bound to vetorized fonts
//			documentTextOrigin = DocumentOrigin.TEXT_ORIGIN_VECTORIZED;
			docOrigin |= DOCUMENT_TEXT_ORIGIN_VECTORIZED_OCR;
//		else if (documentPagesBornDigital) // no indication of scans at all
//			documentTextOrigin = DocumentOrigin.TEXT_ORIGIN_BORN_DIGITAL;
		else if ((scanCount * 3) < (pageCount * 2)) // no indication of scans at all
			docOrigin |= DOCUMENT_TEXT_ORIGIN_BORN_DIGITAL;
//		else documentTextOrigin = DocumentOrigin.TEXT_ORIGIN_DIGITIZED_OCR; // normal digital fonts, but on top of scanned page backgrounds
		else docOrigin |= DOCUMENT_TEXT_ORIGIN_DIGITIZED_OCR; // normal digital fonts, but on top of scanned page backgrounds
		
		//	finally ...
//		return new DocumentOrigin(documentPagesBornDigital, documentTextOrigin);
		return docOrigin;
	}
	
	//	TODO add merging and splitting for paragraphs and blocks, and maybe also for columns
	
	//	TODO add method ensuring line/paragraph/block/column hierarchy ...
	//	TODO ... especially ensuring presence and completeness of lines
	
	//	TODO use the above both from region action provider and from text flow error quick fixes
	
	/**
	 * Retrieve the rows of a table. If table rows are already marked, this
	 * method simply returns them. If no rows are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no row gaps exist, this method returns null.
	 * Newly generated table rows are not attached to the page.
	 * @param table the table whose rows to retrieve
	 * @param minColumnMargin the minimum margin between two columns
	 * @param minColumnCount the minimum number of columns
	 * @return an array holding the table rows
	 */
	public static ImRegion[] getTableRows(ImRegion table) {
		return getTableRows(table,  null);
	}
	
	/**
	 * Retrieve the rows of a table. If table rows are already marked, this
	 * method simply returns them. If no rows are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no row gaps exist, this method returns null.
	 * Newly generated table rows are not attached to the page.
	 * @param table the table whose rows to retrieve
	 * @param ignoreWords a set containing word to ignore if table rows have to
	 *            be newly created
	 * @return an array holding the table rows
	 */
	public static ImRegion[] getTableRows(ImRegion table, Set ignoreWords) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	estimate minimums and get columns
		return getTableRows(table, ignoreWords,  (page.getImageDPI() / 50) /* about 0.5mm */, 0);
	}
	
	/**
	 * Retrieve the rows of a table. If table rows are already marked, this
	 * method simply returns them. If no rows are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no row gaps exist, this method returns null.
	 * Newly generated table rows are not attached to the page.
	 * @param table the table whose rows to retrieve
	 * @param minRowMargin the minimum margin between two rows
	 * @param minRowCount the minimum number of rows
	 * @return an array holding the table rows
	 */
	public static ImRegion[] getTableRows(ImRegion table, int minRowMargin, int minRowCount) {
		return getTableRows(table, null, minRowMargin, minRowCount);
	}
	
	/**
	 * Retrieve the rows of a table. If table rows are already marked, this
	 * method simply returns them. If no rows are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no row gaps exist, this method returns null.
	 * Newly generated table rows are not attached to the page.
	 * @param table the table whose rows to retrieve
	 * @param ignoreWords a set containing word to ignore if table rows have to
	 *            be newly created
	 * @param minRowMargin the minimum margin between two rows
	 * @param minRowCount the minimum number of rows
	 * @return an array holding the table rows
	 */
	public static ImRegion[] getTableRows(ImRegion table, Set ignoreWords, int minRowMargin, int minRowCount) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	try and get existing table rows first
//		ImRegion[] tableRows = getRegionsInside(page, table.bounds, ImRegion.TABLE_ROW_TYPE, false);
		ImRegion[] tableRows = page.getRegionsInside(ImRegion.TABLE_ROW_TYPE, table.bounds, false);
		
		//	generate rows if none exist
		if (tableRows.length == 0)
			tableRows = createTableRows(table, ignoreWords, minRowMargin, minRowCount);
		
		//	finally ...
		return tableRows;
	}
	
	/**
	 * Create the rows of a table. This method always tries to generate table
	 * rows, ignoring any existing ones. If generation fails, e.g. if the
	 * argument region does not contain any words, or if no row gaps exist,
	 * this method returns null. The generated table rows are not attached to
	 * the page.
	 * @param table the table whose rows to retrieve
	 * @return an array holding the table rows
	 */
	public static ImRegion[] createTableRows(ImRegion table) {
		return createTableRows(table, null);
	}
	
	/**
	 * Create the rows of a table. This method always tries to generate table
	 * rows, ignoring any existing ones. If generation fails, e.g. if the
	 * argument region does not contain any words, or if no row gaps exist,
	 * this method returns null. The generated table rows are not attached to
	 * the page.
	 * @param table the table whose rows to retrieve
	 * @param ignoreWords a set containing word to ignore
	 * @return an array holding the table rows
	 */
	public static ImRegion[] createTableRows(ImRegion table, Set ignoreWords) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	estimate minimums and get columns
		return createTableRows(table,  (page.getImageDPI() / 50) /* about 0.5mm */, 0);
	}
	
	/**
	 * Create the rows of a table. This method always tries to generate table
	 * rows, ignoring any existing ones. If generation fails, e.g. if the
	 * argument region does not contain any words, or if no row gaps exist,
	 * this method returns null. The generated table rows are not attached to
	 * the page.
	 * @param table the table whose rows to retrieve
	 * @param minRowMargin the minimum margin between two rows
	 * @param minRowCount the minimum number of rows
	 * @return an array holding the table rows
	 */
	public static ImRegion[] createTableRows(ImRegion table, int minRowMargin, int minRowCount) {
		return createTableRows(table, null, minRowMargin, minRowCount);
	}
	
	/**
	 * Create the rows of a table. This method always tries to generate table
	 * rows, ignoring any existing ones. If generation fails, e.g. if the
	 * argument region does not contain any words, or if no row gaps exist,
	 * this method returns null. The generated table rows are not attached to
	 * the page.
	 * @param table the table whose rows to retrieve
	 * @param ignoreWords a set containing word to ignore
	 * @param minRowMargin the minimum margin between two rows
	 * @param minRowCount the minimum number of rows
	 * @return an array holding the table rows
	 */
	public static ImRegion[] createTableRows(ImRegion table, Set ignoreWords, int minRowMargin, int minRowCount) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	get words
		ImWord[] tableWords = page.getWordsInside(table.bounds);
		if (ignoreWords != null)
			tableWords = filterWords(tableWords, ignoreWords);
		if (tableWords.length == 0)
			return null;
		
		//	assess column occupation
		int[] rowWordCols = new int[table.bounds.bottom - table.bounds.top];
		Arrays.fill(rowWordCols, 0);
		for (int w = 0; w < tableWords.length; w++) {
			for (int r = Math.max(table.bounds.top, tableWords[w].bounds.top); r < Math.min(table.bounds.bottom, tableWords[w].bounds.bottom); r++)
				rowWordCols[r - table.bounds.top] += (tableWords[w].bounds.right - tableWords[w].bounds.left);
		}
		
		//	collect row gaps
		ArrayList rowGaps = new ArrayList();
		TreeSet rowGapWidths = new TreeSet(Collections.reverseOrder());
		collectGaps(rowGaps, rowGapWidths, rowWordCols, minRowMargin, (minRowCount - 1));
		
		//	do we have anything to work with?
		if (rowGaps.isEmpty())
			return null;
		
		//	create row regions
//		ImRegion[] tableRows = new ImRegion[rowGaps.size() + 1];
		ArrayList tableRowList = new ArrayList(rowGaps.size() + 1);
		for (int g = 0; g <= rowGaps.size(); g++) {
			int tableRowTop = table.bounds.top + ((g == 0) ? 0 : ((Gap) rowGaps.get(g-1)).end);
			int tableRowBottom = ((g == rowGaps.size()) ? table.bounds.bottom : (table.bounds.top + ((Gap) rowGaps.get(g)).start));
//			tableRows[g] = new ImRegion(table.getDocument(), table.pageId, new BoundingBox(table.bounds.left, table.bounds.right, tableRowTop, tableRowBottom), ImRegion.TABLE_ROW_TYPE);
			if (tableRowTop < tableRowBottom)
				tableRowList.add(new ImRegion(table.getDocument(), table.pageId, new BoundingBox(table.bounds.left, table.bounds.right, tableRowTop, tableRowBottom), ImRegion.TABLE_ROW_TYPE));
		}
		
		//	finally ...
//		return tableRows;
		return ((ImRegion[]) tableRowList.toArray(new ImRegion[tableRowList.size()]));
	}
	
	/**
	 * Retrieve the columns of a table. If table columns are already marked,
	 * this method simply returns them. If no columns are marked, this method
	 * tries to generate them. If generation fails, e.g. if the argument region
	 * does not contain any words, or if no column gaps exist, this method
	 * returns null. Newly generated table columns are not attached to the page.
	 * @param table the table whose columns to retrieve
	 * @return an array holding the table columns
	 */
	public static ImRegion[] getTableColumns(ImRegion table) {
		return getTableColumns(table, null);
	}
	
	/**
	 * Retrieve the columns of a table. If table columns are already marked,
	 * this method simply returns them. If no columns are marked, this method
	 * tries to generate them. If generation fails, e.g. if the argument region
	 * does not contain any words, or if no column gaps exist, this method
	 * returns null. Newly generated table columns are not attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param ignoreWords a set containing word to ignore if table columns have
	 *            to be newly created
	 * @return an array holding the table columns
	 */
	public static ImRegion[] getTableColumns(ImRegion table, Set ignoreWords) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	estimate minimums and get columns
		return getTableColumns(table, ignoreWords, (page.getImageDPI() / 30) /* less than 1mm */, 0);
	}
	
	/**
	 * Retrieve the columns of a table. If table columns are already marked,
	 * this method simply returns them. If no columns are marked, this method
	 * tries to generate them. If generation fails, e.g. if the argument region
	 * does not contain any words, or if no column gaps exist, this method
	 * returns null. Newly generated table columns are not attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param minColumnMargin the minimum margin between two columns
	 * @param minColumnCount the minimum number of columns
	 * @return an array holding the table columns
	 */
	public static ImRegion[] getTableColumns(ImRegion table, int minColumnMargin, int minColumnCount) {
		return getTableColumns(table, null, minColumnMargin, minColumnCount);
	}
	
	/**
	 * Retrieve the columns of a table. If table columns are already marked,
	 * this method simply returns them. If no columns are marked, this method
	 * tries to generate them. If generation fails, e.g. if the argument region
	 * does not contain any words, or if no column gaps exist, this method
	 * returns null. Newly generated table columns are not attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param ignoreWords a set containing word to ignore if table columns have
	 *            to be newly created
	 * @param minColumnMargin the minimum margin between two columns
	 * @param minColumnCount the minimum number of columns
	 * @return an array holding the table columns
	 */
	public static ImRegion[] getTableColumns(ImRegion table, Set ignoreWords, int minColumnMargin, int minColumnCount) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	try and get existing table columns first
//		ImRegion[] tableCols = getRegionsInside(page, table.bounds, ImRegion.TABLE_COL_TYPE, false);
		ImRegion[] tableCols = page.getRegionsInside(ImRegion.TABLE_COL_TYPE, table.bounds, false);
		
		//	generate columns if none exist
		if (tableCols.length == 0)
			tableCols = createTableColumns(table, ignoreWords, minColumnMargin, minColumnCount);
		
		//	finally ...
		return tableCols;
	}
	
	/**
	 * Create the columns of a table. This method always tries to generate
	 * table columns, ignoring any existing ones. If generation fails, e.g. if
	 * the argument region does not contain any words, or if no column gaps
	 * exist, this method returns null. The generated table columns are not
	 * attached to the page.
	 * @param table the table whose columns to retrieve
	 * @return an array holding the table columns
	 */
	public static ImRegion[] createTableColumns(ImRegion table) {
		return createTableColumns(table, null);
	}
	
	/**
	 * Create the columns of a table. This method always tries to generate
	 * table columns, ignoring any existing ones. If generation fails, e.g. if
	 * the argument region does not contain any words, or if no column gaps
	 * exist, this method returns null. The generated table columns are not
	 * attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param ignoreWords a set containing word to ignore
	 * @return an array holding the table columns
	 */
	public static ImRegion[] createTableColumns(ImRegion table, Set ignoreWords) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	estimate minimums and get columns
		return createTableColumns(table, ignoreWords, (page.getImageDPI() / 30) /* less than 1mm */, 0);
	}
	
	/**
	 * Create the columns of a table. This method always tries to generate
	 * table columns, ignoring any existing ones. If generation fails, e.g. if
	 * the argument region does not contain any words, or if no column gaps
	 * exist, this method returns null. The generated table columns are not
	 * attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param minColumnMargin the minimum margin between two columns
	 * @param minColumnCount the minimum number of columns
	 * @return an array holding the table columns
	 */
	public static ImRegion[] createTableColumns(ImRegion table, int minColumnMargin, int minColumnCount) {
		return createTableColumns(table, null, minColumnMargin, minColumnCount);
	}
	
	/**
	 * Create the columns of a table. This method always tries to generate
	 * table columns, ignoring any existing ones. If generation fails, e.g. if
	 * the argument region does not contain any words, or if no column gaps
	 * exist, this method returns null. The generated table columns are not
	 * attached to the page.
	 * @param table the table whose columns to retrieve
	 * @param ignoreWords a set containing word to ignore
	 * @param minColumnMargin the minimum margin between two columns
	 * @param minColumnCount the minimum number of columns
	 * @return an array holding the table columns
	 */
	public static ImRegion[] createTableColumns(ImRegion table, Set ignoreWords, int minColumnMargin, int minColumnCount) {
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	get words
		ImWord[] tableWords = page.getWordsInside(table.bounds);
		if (ignoreWords != null)
			tableWords = filterWords(tableWords, ignoreWords);
		if (tableWords.length == 0)
			return null;
		
		//	assess column occupation
		int[] colWordRows = new int[table.bounds.getWidth()];
		Arrays.fill(colWordRows, 0);
		for (int w = 0; w < tableWords.length; w++) {
			for (int c = Math.max(table.bounds.left, tableWords[w].bounds.left); c < Math.min(table.bounds.right, tableWords[w].bounds.right); c++)
				colWordRows[c - table.bounds.left] += tableWords[w].bounds.getHeight();
		}
		
		//	collect column gaps
		ArrayList colGaps = new ArrayList();
		TreeSet colGapWidths = new TreeSet(Collections.reverseOrder());
		collectGaps(colGaps, colGapWidths, colWordRows, minColumnMargin, (minColumnCount - 1));
		
		//	do we have anything to work with?
		if (colGaps.isEmpty())
			return null;
		
		//	create column regions
//		ImRegion[] tableCols = new ImRegion[colGaps.size() + 1];
		ArrayList tableColList = new ArrayList(colGaps.size() + 1);
		for (int g = 0; g <= colGaps.size(); g++) {
			int tableColLeft = table.bounds.left + ((g == 0) ? 0 : ((Gap) colGaps.get(g-1)).end);
			int tableColRight = ((g == colGaps.size()) ? table.bounds.right : (table.bounds.left + ((Gap) colGaps.get(g)).start));
//			tableCols[g] = new ImRegion(page.getDocument(), page.pageId, new BoundingBox(tableColLeft, tableColRight, table.bounds.top, table.bounds.bottom), ImRegion.TABLE_COL_TYPE);
			if (tableColLeft < tableColRight)
				tableColList.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(tableColLeft, tableColRight, table.bounds.top, table.bounds.bottom), ImRegion.TABLE_COL_TYPE));
		}
		ImRegion[] tableCols = ((ImRegion[]) tableColList.toArray(new ImRegion[tableColList.size()]));
//		
//		//	compute word density and average space widths in columns
//		int[] colRowCount = new int[tableCols.length];
//		int[] colWordCount = new int[tableCols.length];
//		int[] colWordHeightSum = new int[tableCols.length];
//		int[] colWordSpaceCount = new int[tableCols.length];
//		int[] colWordSpaceWidthSum = new int[tableCols.length];
//		int[] maxColWordSpace = new int[tableCols.length];
//		for (int c = 0; c < tableCols.length; c++) {
//			if (DEBUG_CREATE_TABLE_COLS) System.out.println("Investigating column " + tableCols[c].bounds + " for spaces");
//			int rowBreakCount = 0;
//			int wordHeightSum = 0;
//			int wordSpaceCount = 0;
//			int wordSpaceWidthSum = 0;
//			int minWordSpace = Integer.MAX_VALUE;
//			int maxWordSpace = 0;
//			ImWord[] colWords = page.getWordsInside(tableCols[c].bounds);
//			if (ignoreWords != null)
//				colWords = filterWords(colWords, ignoreWords);
//			if (colWords.length == 0) {
//				colWordCount[c] = 0;
//				colWordHeightSum[c] = 0;
//				colWordSpaceCount[c] = 0;
//				colWordSpaceWidthSum[c] = 0;
//				maxColWordSpace[c] = 0;
//				if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - no words to compute spacing from");
//			}
//			sortLeftRightTopDown(colWords);
//			for (int w = 0; w < colWords.length; w++) {
//				wordHeightSum += (colWords[w].bounds.bottom - colWords[w].bounds.top);
//				if ((w+1) == colWords.length)
//					continue;
//				if (colWords[w].bounds.bottom < colWords[w+1].bounds.top) {
//					rowBreakCount++;
//					continue;
//				}
//				if ((colWords[w].centerY < colWords[w+1].bounds.top) || (colWords[w].centerY >= colWords[w+1].bounds.bottom))
//					continue;
//				int wordSpace = (colWords[w+1].bounds.left - colWords[w].bounds.right);
//				if (wordSpace <= 0)
//					continue;
//				wordSpaceCount++;
//				wordSpaceWidthSum += wordSpace;
//				minWordSpace = Math.min(minWordSpace, wordSpace);
//				maxWordSpace = Math.max(maxWordSpace, wordSpace);
//			}
//			colRowCount[c] = (rowBreakCount + 1);
//			colWordCount[c] = colWords.length;
//			colWordHeightSum[c] = wordHeightSum;
//			colWordSpaceCount[c] = wordSpaceCount;
//			colWordSpaceWidthSum[c] = wordSpaceWidthSum;
//			if (wordSpaceCount == 0) {
//				maxColWordSpace[c] = 0;
//				if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - could not compute word spacing");
//			}
//			else {
//				maxColWordSpace[c] = maxWordSpace;
//				if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - avg word space is " + (((float) wordSpaceWidthSum) / wordSpaceCount) + " (min " + minWordSpace + ", max " + maxColWordSpace[c] + ") from " + wordSpaceCount + " spaces, norm is " + (((float) wordHeightSum) / (colWords.length * 2)));
//			}
//		}
//		
//		//	compute average space width across _all_ columns
//		int rowCountSum = 0;
//		int wordCount = 0;
//		int wordHeightSum = 0;
//		int wordSpaceCount = 0;
//		int wordSpaceWidthSum = 0;
//		for (int c = 0; c < tableCols.length; c++) {
//			rowCountSum += colRowCount[c];
//			wordCount += colWordCount[c];
//			wordHeightSum += colWordHeightSum[c];
//			wordSpaceCount += colWordSpaceCount[c];
//			wordSpaceWidthSum += colWordSpaceWidthSum[c];
//		}
//		float avgRowCount = (((float) rowCountSum) / tableCols.length);
//		if (DEBUG_CREATE_TABLE_COLS) System.out.println("Average row count is " + avgRowCount);
//		float normWordSpace = (((float) wordHeightSum) / (wordCount * 2)); // assume regular space as 0.5, i.e., half the word height of 1.0 (it's actually between 0.25 and 0.33 in most fonts)
//		float maxNormWordSpace = (((float) wordHeightSum) / wordCount); // cap average off at 1.0 (it's rarely wider in any font, if at all, and the cap helps with cases of a shunned column gap being the only space in the table at all)
//		float avgWordSpace = ((wordSpaceCount == 0) ? normWordSpace : (((float) wordSpaceWidthSum) / wordSpaceCount));
//		if (DEBUG_CREATE_TABLE_COLS) System.out.println("Average word space is " + avgWordSpace + " from " + wordSpaceCount + " spaces, norm is " + normWordSpace + ", capped at " + maxNormWordSpace);
//		if (avgWordSpace > maxNormWordSpace) {
//			avgWordSpace = maxNormWordSpace;
//			if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - capped to norm maximum");
//		}
//		else if (avgWordSpace < normWordSpace) {
//			avgWordSpace = normWordSpace;
//			if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - increase to norm");
//		}
//		
//		//	re-assess columns that contain potential further splits
//		for (int c = 0; c < tableCols.length; c++) {
//			if (DEBUG_CREATE_TABLE_COLS) System.out.println("Investigating column " + tableCols[c].bounds + " for splits");
//			if (colWordSpaceCount[c] == 0) {
//				if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - no spaces at all");
//				continue;
//			}
//			if (maxColWordSpace[c] <= avgWordSpace) {
//				if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - all spaces below average");
//				continue;
//			}
//			
//			//	flag each pixel column in each column as non-gap that (a) is covered by a word or (b) by a space below that average
//			boolean[] isWideGap = new boolean[tableCols[c].bounds.right - tableCols[c].bounds.left];
//			Arrays.fill(isWideGap, true);
//			int[] pxColWordCounts = new int[tableCols[c].bounds.right - tableCols[c].bounds.left];
//			Arrays.fill(pxColWordCounts, 0);
//			ImWord[] colWords = page.getWordsInside(tableCols[c].bounds);
//			if (ignoreWords != null)
//				colWords = filterWords(colWords, ignoreWords);
//			sortLeftRightTopDown(colWords);
//			for (int w = 0; w < colWords.length; w++) {
//				for (int pc = colWords[w].bounds.left; pc < colWords[w].bounds.right; pc++) {
//					isWideGap[pc - tableCols[c].bounds.left] = false;
//					pxColWordCounts[pc - tableCols[c].bounds.left]++;
//				}
//				if ((w+1) == colWords.length)
//					continue;
//				if ((colWords[w].centerY < colWords[w+1].bounds.top) || (colWords[w].centerY >= colWords[w+1].bounds.bottom))
//					continue;
//				int wordSpace = (colWords[w+1].bounds.left - colWords[w].bounds.right);
//				if (wordSpace <= avgWordSpace)
//					for (int pc = colWords[w].bounds.right; pc < colWords[w+1].bounds.left; pc++) {
//						isWideGap[pc - tableCols[c].bounds.left] = false;
//						pxColWordCounts[pc - tableCols[c].bounds.left]++;
//					}
//			}
//			
//			//	also assess width of sparse areas next to non-wide gaps
//			boolean[] isSparselyConstrictedGap = new boolean[tableCols[c].bounds.right - tableCols[c].bounds.left];
//			Arrays.fill(isSparselyConstrictedGap, false);
//			for (int pc = 0; pc < pxColWordCounts.length; pc++) {
//				if (colWordRows[pc + (tableCols[c].bounds.left - table.bounds.left)] != 0)
//					continue; // this one _is_ obstructed
//				if (isWideGap[pc])
//					continue; // we've already found this one
//				int gapStart = pc;
//				int gapEnd = (pc+1);
//				while (gapEnd < pxColWordCounts.length) {
//					if (colWordRows[gapEnd + (tableCols[c].bounds.left - table.bounds.left)] != 0)
//						break;
//					else gapEnd++;
//				}
//				int gapWidth = (gapEnd - gapStart);
//				for (int sl = (gapStart - 1); sl >= 0; sl--) {
//					if ((pxColWordCounts[sl] * 3) < avgRowCount)
//						gapWidth++;
//					else break;
//				}
//				for (int sr = gapStart; sr < pxColWordCounts.length; sr++) {
//					if ((pxColWordCounts[sr] * 3) < avgRowCount)
//						gapWidth++;
//					else break;
//				}
//				if (gapWidth > avgWordSpace) {
//					for (int gc = gapStart; gc < gapEnd; gc++)
//						isSparselyConstrictedGap[gc] = true;
//				}
//			}
//			
//			/* split up column if any space pixel columns remain; it's OK to
//			 * check both wide gaps and sparsely constricted ones in a single
//			 * loop, as they cannot be adjacent to one another: each instance
//			 * of the latter would be absorbed in an instance of the former
//			 * if they were adjacent */
//			ArrayList subColGaps = new ArrayList();
//			int gapStart = -1;
//			for (int pc = 0; pc < isWideGap.length; pc++) {
//				if (isWideGap[pc]) {
//					if (gapStart == -1)
//						gapStart = (pc + (tableCols[c].bounds.left - table.bounds.left));
//				}
//				else if (isSparselyConstrictedGap[pc]) {
//					if (gapStart == -1)
//						gapStart = (pc + (tableCols[c].bounds.left - table.bounds.left));
//				}
//				else {
//					if (gapStart != -1) {
//						int gapEnd = (pc + (tableCols[c].bounds.left - table.bounds.left));
//						subColGaps.add(new Gap(gapStart, gapEnd));
//						if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - found gap from " + (table.bounds.left + gapStart) + " to " + (table.bounds.left + gapEnd));
//					}
//					gapStart = -1;
//				}
//			}
//			
//			//	any new gaps found?
//			if (subColGaps.isEmpty())
//				continue;
//			
//			//	assess maximum row count on both sides of gaps, just to make sure we're not merely splitting some protruding word off a column
//			ArrayList subColMaxRowCounts = new ArrayList(subColGaps.size() + 1);
//			for (int g = 0; g <= subColGaps.size(); g++) {
//				int subColLeft = ((g == 0) ? tableCols[c].bounds.left : (table.bounds.left + ((Gap) subColGaps.get(g-1)).end));
//				int subColRight = ((g == subColGaps.size()) ? tableCols[c].bounds.right : (table.bounds.left + ((Gap) subColGaps.get(g)).start));
//				int maxRowCount = 0;
//				for (int pc = subColLeft; pc < subColRight; pc++)
//					maxRowCount = Math.max(maxRowCount, pxColWordCounts[pc - tableCols[c].bounds.left]);
//				subColMaxRowCounts.add(new Integer(maxRowCount));
//			}
//			
//			if (DEBUG_CREATE_TABLE_COLS) {
//				System.out.println("Got " + subColGaps.size() + " gaps in column " + tableCols[c].bounds + ", in total:");
//				for (int sc = 0; sc < subColMaxRowCounts.size(); sc++) {
//					Integer maxRowCount = ((Integer) subColMaxRowCounts.get(sc));
//					System.out.println(" - sub column with at most " + maxRowCount + " rows occupied");
//					if (sc < subColGaps.size()) {
//						Gap gap = ((Gap) subColGaps.get(sc));
//						System.out.println(" - gap from " + (table.bounds.left + gap.start) + " to " + (table.bounds.left + gap.end));
//					}
//				}
//			}
//			
//			/* sort out gaps that look more like splitting up column values,
//			 * e.g. spaces aligned atop one another; that would occur, for
//			 * instance, with a left-aligned column holding 'Fiji' in 9 rows,
//			 * and 'American Samoa' in 1 row, resulting in a sparsely
//			 * constricted split between 'American' and 'Samoa' */
//			for (int sc = 0; sc < subColMaxRowCounts.size(); sc++) {
//				Integer maxRowCount = ((Integer) subColMaxRowCounts.get(sc));
//				
//				//	this one looks fine, well occupied
//				if ((maxRowCount.intValue() * 3) > avgRowCount)
//					continue;
//				
//				//	get adjacent gaps, and remove smaller one
//				Gap leftGap = ((sc == 0) ? null : ((Gap) subColGaps.get(sc-1)));
//				Gap rightGap = ((sc == subColGaps.size()) ? null : ((Gap) subColGaps.get(sc)));
//				
//				//	we've eliminated everything ...
//				if ((leftGap == null) && (rightGap == null))
//					break;
//				
//				//	we're in leftmost column, can only merge to right
//				if (leftGap == null) {
//					subColGaps.remove(sc);
//					Integer nextMaxRowCount = ((Integer) subColMaxRowCounts.get(sc+1));
//					if (nextMaxRowCount.intValue() < maxRowCount.intValue())
//						subColMaxRowCounts.remove(sc+1);
//					else subColMaxRowCounts.remove(sc);
//					if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - gap from " + (table.bounds.left + rightGap.start) + " to " + (table.bounds.left + rightGap.end) + " eliminated for sparse left column on very left");
//				}
//				
//				//	we're in rightmost column, can only merge to left
//				else if (rightGap == null) {
//					subColGaps.remove(sc-1);
//					Integer prevMaxRowCount = ((Integer) subColMaxRowCounts.get(sc-1));
//					if (prevMaxRowCount.intValue() < maxRowCount.intValue())
//						subColMaxRowCounts.remove(sc-1);
//					else subColMaxRowCounts.remove(sc);
//					if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - gap from " + (table.bounds.left + leftGap.start) + " to " + (table.bounds.left + leftGap.end) + " eliminated for sparse right column on very right");
//				}
//				
//				//	right gap smaller than left one, remove former
//				else if (rightGap.getWidth() < leftGap.getWidth()) {
//					subColGaps.remove(sc);
//					Integer nextMaxRowCount = ((Integer) subColMaxRowCounts.get(sc+1));
//					if (nextMaxRowCount.intValue() < maxRowCount.intValue())
//						subColMaxRowCounts.remove(sc+1);
//					else subColMaxRowCounts.remove(sc);
//					if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - gap from " + (table.bounds.left + rightGap.start) + " to " + (table.bounds.left + rightGap.end) + " eliminated for sparse left column");
//				}
//				
//				//	right gap larger than or equal to left one, remove latter (left aligned columns are way more frequent that right aligned ones, especially with textual content)
//				else {
//					subColGaps.remove(sc-1);
//					Integer prevMaxRowCount = ((Integer) subColMaxRowCounts.get(sc-1));
//					if (prevMaxRowCount.intValue() < maxRowCount.intValue())
//						subColMaxRowCounts.remove(sc-1);
//					else subColMaxRowCounts.remove(sc);
//					if (DEBUG_CREATE_TABLE_COLS) System.out.println(" - gap from " + (table.bounds.left + leftGap.start) + " to " + (table.bounds.left + leftGap.end) + " eliminated for sparse right column");
//				}
//				
//				//	counter loop increment, as we need to come back whichever way we have merged
//				sc--;
//			}
//			
//			//	add whatever gaps we've retained to general ones
//			colGaps.addAll(subColGaps);
//		}
//		
//		//	re-split columns if any new gaps found
//		if (colGaps.size() >= tableCols.length) {
//			Collections.sort(colGaps);
////			tableCols = new ImRegion[colGaps.size() + 1];
//			tableColList.clear();
//			for (int g = 0; g <= colGaps.size(); g++) {
//				int tableColLeft = table.bounds.left + ((g == 0) ? 0 : ((Gap) colGaps.get(g-1)).end);
//				int tableColRight = ((g == colGaps.size()) ? table.bounds.right : (table.bounds.left + ((Gap) colGaps.get(g)).start));
////				tableCols[g] = new ImRegion(page.getDocument(), page.pageId, new BoundingBox(tableColLeft, tableColRight, table.bounds.top, table.bounds.bottom), ImRegion.TABLE_COL_TYPE);
//				if (tableColLeft < tableColRight)
//					tableColList.add(new ImRegion(page.getDocument(), page.pageId, new BoundingBox(tableColLeft, tableColRight, table.bounds.top, table.bounds.bottom), ImRegion.TABLE_COL_TYPE));
//			}
//			tableCols = ((ImRegion[]) tableColList.toArray(new ImRegion[tableColList.size()]));
//		}
		
		//	finally ...
		return tableCols;
	}
//	private static final boolean DEBUG_CREATE_TABLE_COLS = false;
	
	private static class Gap implements Comparable {
		int start;
		int end;
		Gap(int start, int end) {
			this.start = start;
			this.end = end;
		}
		int getWidth() {
			return (this.end - this.start);
		}
		public int compareTo(Object obj) {
			if (obj instanceof Gap)
				return (this.start - ((Gap) obj).start);
			else return -1;
		}
	}
	
	private static void collectGaps(ArrayList gaps, TreeSet gapWidths, int[] wordDensity, int minGapWidth, int minGapCount) {
		
		//	collect gaps
		int gapStart = -1;
		for (int d = 0; d < wordDensity.length; d++) {
			if (wordDensity[d] == 0) {
				if (gapStart == -1)
					gapStart = d;
			}
			else if (gapStart != -1) {
				if (minGapWidth <= (d - gapStart)) {
					Gap gap = new Gap(gapStart, d);
					gaps.add(gap);
					gapWidths.add(new Integer(gap.getWidth()));
				}
				gapStart = -1;
			}
		}
		
		//	find best gap width fulfilling minimum width and count
		int maxScore = 0;
		int maxScoreGapWidth = wordDensity.length;
		for (Iterator cgwit = gapWidths.iterator(); cgwit.hasNext();) {
			int gapWidth = ((Integer) cgwit.next()).intValue();
			int gapCount = 0;
			for (int g = 0; g < gaps.size(); g++) {
				if (((Gap) gaps.get(g)).getWidth() >= gapWidth)
					gapCount++;
			}
			if ((gapCount >= minGapCount) && ((gapCount * gapWidth) > maxScore)) {
				maxScore = (gapCount * gapWidth);
				maxScoreGapWidth = gapWidth;
			}
		}
		
		//	sort out gaps that are too narrow
		for (int g = 0; g < gaps.size(); g++) {
			if (((Gap) gaps.get(g)).getWidth() < maxScoreGapWidth)
				gaps.remove(g--);
		}
	}
	
	private static ImWord[] filterWords(ImWord[] words, Set ignoreWords) {
		if (words.length == 0)
			return words;
		ArrayList wordList = new ArrayList(words.length);
		for (int w = 0; w < words.length; w++) {
			if (!ignoreWords.contains(words[w]))
				wordList.add(words[w]);
		}
		return ((wordList.size() < words.length) ? ((ImWord[]) wordList.toArray(new ImWord[wordList.size()])) : words);
	}
	
	/**
	 * Retrieve the cells of a table. If table cells are already marked, this
	 * method simply returns them. If no cells are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no column or row gaps exist, this method
	 * returns null. Newly generated table cells are not attached to the page.
	 * @param table the table whose cells to retrieve
	 * @param rows an array holding existing table rows (may be null)
	 * @param cols an array holding existing table columns (may be null)
	 * @return an array holding the table cells (rows in outer dimension)
	 */
	public static ImRegion[][] getTableCells(ImRegion table, ImRegion[] rows, ImRegion[] cols) {
		return getTableCells(table, null, rows, cols);
	}
	
	/**
	 * Retrieve the cells of a table. If table cells are already marked, this
	 * method simply returns them. If no cells are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no column or row gaps exist, this method
	 * returns null. Newly generated table cells are not attached to the page.
	 * @param table the table whose cells to retrieve
	 * @param ignoreWords a set containing word to ignore if table columns or
	 *            rows have to be newly created
	 * @param rows an array holding existing table rows (may be null)
	 * @param cols an array holding existing table columns (may be null)
	 * @return an array holding the table cells (rows in outer dimension)
	 */
	public static ImRegion[][] getTableCells(ImRegion table, Set ignoreWords, ImRegion[] rows, ImRegion[] cols) {
		return getTableCells(table, ignoreWords, rows, cols, true);
	}
	
	/**
	 * Retrieve the cells of a table. If table cells are already marked, this
	 * method simply returns them. If no cells are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no column or row gaps exist, this method
	 * returns null. Newly generated table cells are not attached to the page.
	 * @param table the table whose cells to retrieve
	 * @param ignoreWords a set containing word to ignore if table columns or
	 *            rows have to be newly created
	 * @param rows an array holding existing table rows (may be null)
	 * @param cols an array holding existing table columns (may be null)
	 * @param cleanUpSpurious remove cells not matching any column/row
	 *            intersection?
	 * @return an array holding the table cells (rows in outer dimension)
	 */
	public static ImRegion[][] getTableCells(ImRegion table, Set ignoreWords, ImRegion[] rows, ImRegion[] cols, boolean cleanUpSpurious) {
		
		//	get text direction
		String textDirection = ((String) table.getAttribute(ImRegion.TEXT_DIRECTION_ATTRIBUTE, ImRegion.TEXT_DIRECTION_LEFT_RIGHT));
		
		//	get rows and columns if not given
		if (rows == null) {
			rows = getTableRows(table, ignoreWords);
			if (rows == null)
				return null;
		}
		if (cols == null) {
			cols = getTableColumns(table, ignoreWords);
			if (cols == null)
				return null;
		}
		
		//	sort rows and columns
		Arrays.sort(rows, getTableRowOrder(textDirection));
		Arrays.sort(cols, getTableColumnOrder(textDirection));
		
		//	get page
		ImPage page = table.getPage();
		if (page == null)
			page = table.getDocument().getPage(table.pageId);
		
		//	get and index existing cells
		ImRegion[] existingCells = table.getRegions(ImRegion.TABLE_CELL_TYPE);
		HashMap existingCellsByBounds = new HashMap();
		for (int c = 0; c < existingCells.length; c++)
			existingCellsByBounds.put(existingCells[c].bounds.toString(), existingCells[c]);
		
		//	get current cells
		ImRegion[][] cells = new ImRegion[rows.length][cols.length];
		for (int r = 0; r < rows.length; r++)
			for (int c = 0; c < cols.length; c++) {
				
				//	create cell bounds TODOne observe text orientation
//				BoundingBox cellBounds = new BoundingBox(cols[c].bounds.left, cols[c].bounds.right, rows[r].bounds.top, rows[r].bounds.bottom);
				BoundingBox cellBounds;// = new BoundingBox(cols[c].bounds.left, cols[c].bounds.right, rows[r].bounds.top, rows[r].bounds.bottom);
				if (ImRegion.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection))
					cellBounds = new BoundingBox(rows[r].bounds.left, rows[r].bounds.right, cols[c].bounds.top, cols[c].bounds.bottom);
				else if (ImRegion.TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
					cellBounds = new BoundingBox(rows[r].bounds.left, rows[r].bounds.right, cols[c].bounds.top, cols[c].bounds.bottom);
				else cellBounds = new BoundingBox(cols[c].bounds.left, cols[c].bounds.right, rows[r].bounds.top, rows[r].bounds.bottom);
				
				//	get cell words
				ImWord[] cellWords = page.getWordsInside(cellBounds);
				if (ignoreWords != null)
					cellWords = filterWords(cellWords, ignoreWords);
//				
//				//	shrink cell bounds to words TODOne omit this
//				if (cellWords.length != 0) {
//					int cbLeft = cellBounds.right;
//					int cbRight = cellBounds.left;
//					int cbTop = cellBounds.bottom;
//					int cbBottom = cellBounds.top;
//					for (int w = 0; w < cellWords.length; w++) {
//						cbLeft = Math.min(cbLeft, cellWords[w].bounds.left);
//						cbRight = Math.max(cbRight, cellWords[w].bounds.right);
//						cbTop = Math.min(cbTop, cellWords[w].bounds.top);
//						cbBottom = Math.max(cbBottom, cellWords[w].bounds.bottom);
//					}
//					cellBounds = new BoundingBox(cbLeft, cbRight, cbTop, cbBottom);
//				}
				
				//	get existing single-column single-row cell
				cells[r][c] = ((ImRegion) existingCellsByBounds.remove(cellBounds.toString()));
				if (cells[r][c] != null)
					continue;
				
				//	find existing multi-column and/or multi-row cell
				/* (This nested loop join should be pretty fast in practice, as
				 * either we have many existing cells and the above map lookup
				 * catches the very most, or we have no or few existing cells
				 * and thus the nested loop has very few rounds.) */
				for (int e = 0; e < existingCells.length; e++)
					if (existingCells[e].bounds.includes(cellBounds, false)) {
						cells[r][c] = existingCells[e];
						existingCellsByBounds.remove(existingCells[e].bounds.toString());
						break;
					}
				if (cells[r][c] != null)
					continue;
				
				//	create new cell
				cells[r][c] = new ImRegion(page.getDocument(), page.pageId, cellBounds, ImRegion.TABLE_CELL_TYPE);
			}
		
		//	remove spurious cells
		if (cleanUpSpurious)
			for (Iterator cit = existingCellsByBounds.values().iterator(); cit.hasNext();) {
				ImRegion cell = ((ImRegion) cit.next());
				if (cell.getPage() != null)
					page.removeRegion(cell);
			}
		
		//	finally ...
		return cells;
	}
	
	private static Comparator getTableRowOrder(String textDirection) {
		if (ImRegion.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection))
			return leftRightOrder;
		else if (ImRegion.TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
			return Collections.reverseOrder(leftRightOrder);
		else return topDownOrder;
	}
	
	private static Comparator getTableColumnOrder(String textDirection) {
		if (ImRegion.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection))
			return Collections.reverseOrder(topDownOrder);
		else if (ImRegion.TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
			return topDownOrder;
		else return leftRightOrder;
	}
	
	/**
	 * Retrieve the cells of a table. If table cells are already marked, this
	 * method simply returns them. If no cells are marked, this method tries to
	 * generate them. If generation fails, e.g. if the argument region does not
	 * contain any words, or if no column or row gaps exist, this method
	 * returns null. Newly generated table cells are attached to the page.
	 * @param table the table whose cells to retrieve
	 * @return an array holding the table cells (rows in outer dimension)
	 */
	public static ImRegion[][] markTableCells(ImPage page, ImRegion table) {
		
		//	get cells
		ImRegion[][] cells = getTableCells(table, null, null);
		
		//	make sure cells are attached to page
		for (int r = 0; r < cells.length; r++)
			for (int c = 0; c < cells[r].length; c++) {
				if (cells[r][c].getPage() == null)
					page.addRegion(cells[r][c]);
			}
		
		//	finally ...
		return cells;
	}
	
	/** operation resolving cell boundary conflicts by splitting violating annotations to the individual cells */
	public static final String CONFLICT_RESOLUTION_OPERATION_SPLIT = "split";
	
	/** operation resolving cell boundary conflicts by removing violating annotations altogether */
	public static final String CONFLICT_RESOLUTION_OPERATION_REMOVE = "remove";
	
	/** operation resolving cell boundary conflicts by truncating violating annotations to the cell they start in */
	public static final String CONFLICT_RESOLUTION_OPERATION_CUT_TO_START = "cutToStart";
	
	/** operation resolving cell boundary conflicts by truncating violating annotations to the cell they end in */
	public static final String CONFLICT_RESOLUTION_OPERATION_CUT_TO_END = "cutToEnd";
	
	/**
	 * Clean up the annotations in a table, i.e., make sure they do not cross
	 * cell boundaries. Default behavior is to split violating annotations up
	 * into the individual cells. The argument map can specify other operations
	 * for individual annotation types, i.e., indicate to remove violating
	 * annotations altogether, or to truncate them to the cell they start or
	 * end in.
	 * @param doc the document the table belongs to
	 * @param table the table to clean up
	 * @param conflictResolutionOperations a map indicating how to resolve
	 *            conflicts for individual annotation types
	 */
	public static void cleanupTableAnnotations(ImDocument doc, ImRegion table, Properties conflictResolutionOperations) {
		
		//	get annotations lying inside table
		ImAnnotation[] annots = doc.getAnnotations(table.pageId);
		ArrayList annotList = new ArrayList();
		for (int a = 0; a < annots.length; a++) {
			if (!ImWord.TEXT_STREAM_TYPE_TABLE.equals(annots[a].getFirstWord().getTextStreamType()))
				continue;
			if (table.pageId != annots[a].getFirstWord().pageId)
				continue;
			if (table.pageId != annots[a].getLastWord().pageId)
				continue;
			if (!table.bounds.includes(annots[a].getFirstWord().bounds, true))
				continue;
			if (!table.bounds.includes(annots[a].getLastWord().bounds, true))
				continue;
			annotList.add(annots[a]);
		}
		if (annotList.isEmpty())
			return;
		
		//	get and sort table cells
		ImRegion[] cells = table.getRegions(ImRegion.TABLE_CELL_TYPE);
		if (cells.length == 0)
			return;
		Arrays.sort(cells, leftRightOrder);
		Arrays.sort(cells, topDownOrder);
		
		//	get table words
		ImWord[] words = table.getWords();
		if (words.length == 0)
			return;
		Arrays.sort(words, textStreamOrder);
		
		//	index cells by words
		HashMap wordsToCells = new HashMap();
		ImRegion wordCell = null;
		for (int w = 0; w < words.length; w++) {
			if ((wordCell != null) && (wordCell.bounds.includes(words[w].bounds, true)))
				wordsToCells.put(words[w], wordCell);
			else for (int c = 0; c < cells.length; c++)
				if (cells[c].bounds.includes(words[w].bounds, true)) {
					wordCell = cells[c];
					wordsToCells.put(words[w], wordCell);
					break;
				}
		}
		
		//	mark parts of original annotation in individual cells
		for (int a = 0; a < annotList.size(); a++) {
			ImAnnotation annot = ((ImAnnotation) annotList.get(a));
			String confResOp = ((conflictResolutionOperations == null) ? CONFLICT_RESOLUTION_OPERATION_SPLIT : conflictResolutionOperations.getProperty(annot.getType(), CONFLICT_RESOLUTION_OPERATION_SPLIT));
			cleanupTableAnnotation(doc, annot, wordsToCells, confResOp);
		}
		
		//	perform document annotation cleanup to deal with whatever duplicates we might have incurred
		doc.cleanupAnnotations();
	}
	
	/**
	 * Clean up a specific annotation in a table, i.e., make sure it does not
	 * cross any cell boundaries. Default behavior is to split the annotation
	 * up into the individual cells. The argument operation can specify other
	 * behavior, i.e., indicate to remove the  annotation altogether in case of
	 * a conflict, or to truncate it to the cell it starts or ends in.
	 * @param doc the document the table belongs to
	 * @param annotation the annotation to check
	 * @param table the table to clean up
	 * @param conflictResolutionOperation a string indicating how to resolve a
	 *            conflict for the argument annotation
	 */
	public static void cleanupTableAnnotation(ImDocument doc, ImAnnotation annotation, String conflictResolutionOperation) {
		
		//	get and sort table cells
		ImPage page = doc.getPage(annotation.getFirstWord().pageId);
		if (page == null)
			return;
		ImRegion[] cells = page.getRegions(ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(cells, leftRightOrder);
		Arrays.sort(cells, topDownOrder);
		
		//	index cells by words
		HashMap wordsToCells = new HashMap();
		ImRegion wordCell = null;
		for (ImWord imw = annotation.getFirstWord(); imw != null; imw = imw.getNextWord()) {
			if ((wordCell != null) && (wordCell.bounds.includes(imw.bounds, true)))
				wordsToCells.put(imw, wordCell);
			else for (int c = 0; c < cells.length; c++)
				if (cells[c].bounds.includes(imw.bounds, true)) {
					wordCell = cells[c];
					wordsToCells.put(imw, wordCell);
					break;
				}
			if (imw == annotation.getLastWord())
				break;
		}
		
		//	finally ...
		cleanupTableAnnotation(doc, annotation, wordsToCells, conflictResolutionOperation);
	}
	
	private static void cleanupTableAnnotation(ImDocument doc, ImAnnotation annotation, HashMap wordsToCells, String conflictResolutionOperation) {
		
		//	single-cell annotation, nothing to chop
		if (wordsToCells.get(annotation.getFirstWord()) == wordsToCells.get(annotation.getLastWord()))
			return;
		
		//	remove annotation (happens below)
		if (CONFLICT_RESOLUTION_OPERATION_REMOVE.equals(conflictResolutionOperation))
			doc.removeAnnotation(annotation);
		
		//	cut at end of start cell
		else if (CONFLICT_RESOLUTION_OPERATION_CUT_TO_START.equals(conflictResolutionOperation)) {
			ImRegion annotStartCell = ((ImRegion) wordsToCells.get(annotation.getFirstWord()));
			for (ImWord imw = annotation.getFirstWord(); imw != null; imw = imw.getNextWord())
				if (wordsToCells.get(imw) != annotStartCell) {
					annotation.setLastWord(imw.getPreviousWord());
					break;
				}
		}
		
		//	cut at start of end cell
		else if (CONFLICT_RESOLUTION_OPERATION_CUT_TO_END.equals(conflictResolutionOperation)) {
			ImRegion annotEndCell = ((ImRegion) wordsToCells.get(annotation.getLastWord()));
			for (ImWord imw = annotation.getLastWord(); imw != null; imw = imw.getPreviousWord())
				if (wordsToCells.get(imw) != annotEndCell) {
					annotation.setFirstWord(imw.getNextWord());
					break;
				}
		}
		
		//	split up to individual cells (the default)
		else /*if (CONFLICT_RESOLUTION_OPERATION_SPLIT.equals(conflictResolutionOperation)) */{
			ImRegion annotCell = ((ImRegion) wordsToCells.get(annotation.getFirstWord()));
			ImAnnotation annot = doc.addAnnotation(annotation.getFirstWord(), annotation.getType());
			annot.copyAttributes(annotation);
			for (ImWord imw = annotation.getFirstWord(); imw != null; imw = imw.getNextWord()) {
				if (wordsToCells.get(imw) != annotCell) {
					annot.setLastWord(imw.getPreviousWord());
					annot = doc.addAnnotation(imw, annotation.getType());
					annot.copyAttributes(annotation);
					annotCell = ((ImRegion) wordsToCells.get(imw));
				}
				if (imw == annotation.getLastWord()) {
					annot.setLastWord(imw);
					break;
				}
			}
			doc.removeAnnotation(annotation); //	clean up original annotation
		}
	}
	
	/**
	 * Order the words in a table. This method first makes the words from each
	 * cell into a separate logical stream, then concatenates these streams
	 * across table rows, and finally concatenates all the rows. All cells have
	 * to be attached to document pages for this method to have any effect.
	 * @param cells the table cells
	 */
	public static void orderTableWords(ImRegion[][] cells) {
		for (int r = 0; r < cells.length; r++)
			for (int c = 0; c < cells[r].length; c++) {
				if (cells[r][c].getPage() == null)
					return;
			}
		
		ImWord lastCellEnd = null;
		for (int r = 0; r < cells.length; r++)
			for (int c = 0; c < cells[r].length; c++) {
				if ((c != 0) && (cells[r][c] == cells[r][c-1]))
					continue; // seen this multi-column cell before
				if ((r != 0) && (cells[r][c] == cells[r-1][c]))
					continue; // seen this multi-row cell before
				
				//	get words
				ImWord[] cellWords = cells[r][c].getWords();
				if (cellWords.length == 0)
					continue; // nothing to order here
				
				//	assess word orientation
				int lrWords = 0;
				int buWords = 0;
				int tdWords = 0;
				int udWords = 0;
				for (int w = 0; w < cellWords.length; w++) {
					String wordDirection = ((String) (cellWords[w].getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT)));
					if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(wordDirection))
						buWords++;
					else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(wordDirection))
						tdWords++;
					else if (ImWord.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(wordDirection))
						udWords++;
					else lrWords++;
				}
				
				//	cut out cell words to avoid order mix-up errors (cut annotations as well, must not cross cell boundaries anyway)
//				System.out.println("Ordering words in " + cells[r][c].bounds + ": " + Arrays.toString(cellWords));
				makeStream(cellWords, null, null, true);
				
				//	order cell words dependent on direction
				if ((lrWords < buWords) && (tdWords < buWords) && (udWords < buWords))
					orderStream(cellWords, bottomUpLeftRightOrder);
				else if ((lrWords < tdWords) && (buWords < tdWords) && (udWords < tdWords))
					orderStream(cellWords, topDownRightLeftOrder);
				else if ((lrWords < udWords) && (buWords < udWords) && (tdWords < udWords))
					orderStream(cellWords, rightLeftBottomUpOrder);
				else orderStream(cellWords, leftRightTopDownOrder);
//				System.out.println(" ==> " + Arrays.toString(cellWords));
				
				//	remove cell internal paragraph breaks
				for (int w = 0; w < (cellWords.length - 1); w++) {
					if (cellWords[w].getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
						cellWords[w].setNextRelation(ImWord.NEXT_RELATION_SEPARATE);
				}
				
				//	attach cell word stream
				Arrays.sort(cellWords, textStreamOrder);
//				System.out.println(" ==> " + Arrays.toString(cellWords));
				if (lastCellEnd != null)
					cellWords[0].setPreviousWord(lastCellEnd);
				lastCellEnd = cellWords[cellWords.length-1];
			}
	}
	
	/**
	 * Test if the rows of two tables are compatible. In particular, this means
	 * that (a) the tables have the same number of rows, and (b) the rows have
	 * the same leading labels. In addition, both tables have to be attached to
	 * their pages.
	 * @param table1 the first table
	 * @param table2 the second table
	 * @return true if the rows of the argument tables are compatible
	 */
	public static boolean areTableRowsCompatible(ImRegion table1, ImRegion table2) {
		return areTableRowsCompatible(table1, table2, true);
	}
	
	/**
	 * Test if the rows of two tables are compatible. In particular, this means
	 * that (a) the tables have the same number of rows, and (b, if desired)
	 * the rows have the same leading labels. In addition, both tables have to
	 * be attached to their pages.
	 * @param table1 the first table
	 * @param table2 the second table
	 * @param compareLabels compare row labels?
	 * @return true if the rows of the argument tables are compatible
	 */
	public static boolean areTableRowsCompatible(ImRegion table1, ImRegion table2, boolean compareLabels) {
		if ((table1.getPage() == null) || (table2.getPage() == null))
			return false;
		
		//	get cells
		ImRegion[][] cells1 = getTableCells(table1, null, null);
		if (cells1 == null)
			return false;
		ImRegion[][] cells2 = getTableCells(table2, null, null);
		if (cells2 == null)
			return false;
		
		//	do we have the same number of rows?
		if (cells1.length != cells2.length)
			return false;
		
		//	if we're not comparing row labels, we're done
		if (!compareLabels)
			return true;
		
		//	get and compare row labels
		String[] labels1 = getTableRowLabels(cells1, table1.getPage());
		String[] labels2 = getTableRowLabels(cells2, table2.getPage());
		if ((labels1 == null) || (labels2 == null) || (labels1.length != labels2.length))
			return false;
		for (int l = 1; l < labels1.length; l++) {
			if (!labels1[l].equals(labels2[l]))
				return false;
		}
		
		//	we're OK
		return true;
	}
	
	/**
	 * Obtain the row labels of a table. If the argument table is not attached
	 * to its page, this method returns null. The length of the returned array
	 * is exactly the number of rows in the argument table, with empty labels
	 * represented by empty strings; no array entry is null.
	 * @param table the table to obtain the row labels for
	 * @return an array holding the row labels
	 */
	public static String[] getTableRowLabels(ImRegion table) {
		if (table.getPage() == null)
			return null;
		
		//	get cells
		ImRegion[][] cells = getTableCells(table, null, null);
		if (cells == null)
			return null;
		
		//	return row labels
		return getTableRowLabels(cells, table.getPage());
	}
	
	private static String[] getTableRowLabels(ImRegion[][] cells, ImPage page) {
		String[] rowLabels = new String[cells.length];
		for (int r = 0; r < cells.length; r++) {
			if ((r != 0) && (cells[r][0] == cells[r-1][0])) {
				rowLabels[r] = rowLabels[r-1];
//				continue;
			}
//			ImWord[] words;
//			if (cells[r][0].getPage() == null)
//				words = page.getWordsInside(cells[r][0].bounds);
//			else words = cells[r][0].getWords();
//			if (words.length == 0)
//				rowLabels[r] = "";
//			else {
//				Arrays.sort(words, textStreamOrder);
//				rowLabels[r] = getString(words[0], words[words.length-1], true);
//			}
			else rowLabels[r] = getCellString(cells[r][0], page);
		}
		return rowLabels;
	}
	
	/**
	 * Connect two tables side by side, conceptually concatenating their rows.
	 * If the columns of the argument tables are connected to other tables, the
	 * connected tables must be compatible as well, and if so, their rows are
	 * also connected.
	 * @param leftTable the left table
	 * @param rightTable the right table
	 * @return true if the connection was successful, false otherwise
	 */
	public static boolean connectTableRows(ImRegion leftTable, ImRegion rightTable) {
		ImRegion[] leftTables = getColumnConnectedTables(leftTable);
		ImRegion[] rightTables = getColumnConnectedTables(rightTable);
		if (leftTables.length != rightTables.length)
			return false;
		for (int t = 0; t < leftTables.length; t++) {
			if (!areTableRowsCompatible(leftTables[t], rightTables[t]))
				return false;
		}
		for (int t = 0; t < leftTables.length; t++) {
			leftTables[t].setAttribute("rowsContinueIn", (rightTables[t].pageId + "." + rightTables[t].bounds));
			rightTables[t].setAttribute("rowsContinueFrom", (leftTables[t].pageId + "." + leftTables[t].bounds));
		}
		return true;
	}
	
	/**
	 * Test if the columns of two tables are compatible. In particular, this
	 * means that (a) the tables have the same number of columns, and (b) the
	 * columns have the same headers. In addition, both tables have to be
	 * attached to their pages.
	 * @param table1 the first table
	 * @param table2 the second table
	 * @return true if the columns of the argument tables are compatible
	 */
	public static boolean areTableColumnsCompatible(ImRegion table1, ImRegion table2) {
		return areTableColumnsCompatible(table1, table2, true);
	}
	
	/**
	 * Test if the columns of two tables are compatible. In particular, this
	 * means that (a) the tables have the same number of columns, and (b, if
	 * desired) the columns have the same headers. In addition, both tables
	 * have to be attached to their pages.
	 * @param table1 the first table
	 * @param table2 the second table
	 * @param compareHeaders compare row labels?
	 * @return true if the columns of the argument tables are compatible
	 */
	public static boolean areTableColumnsCompatible(ImRegion table1, ImRegion table2, boolean compareHeaders) {
		if ((table1.getPage() == null) || (table2.getPage() == null))
			return false;
		
		//	get cells
		ImRegion[][] cells1 = getTableCells(table1, null, null);
		if (cells1 == null)
			return false;
		ImRegion[][] cells2 = getTableCells(table2, null, null);
		if (cells2 == null)
			return false;
		
		//	do we have the same number of columns
		if (cells1[0].length != cells2[0].length)
			return false;
		
		//	if we're not comparing row labels, we're done
		if (!compareHeaders)
			return true;
		
		//	get and compare column headers
		String[] labels1 = getTableColumnHeaders(cells1, table1.getPage());
		String[] labels2 = getTableColumnHeaders(cells2, table2.getPage());
		if ((labels1 == null) || (labels2 == null) || (labels1.length != labels2.length))
			return false;
		for (int l = 1; l < labels1.length; l++) {
			if (!labels1[l].equals(labels2[l]))
				return false;
		}
		
		//	we're OK
		return true;
	}
	
	/**
	 * Obtain the row labels of a table. If the argument table is not attached
	 * to its page, this method returns null. The length of the returned array
	 * is exactly the number of rows in the argument table, with empty labels
	 * represented by empty strings; no array entry is null.
	 * @param table the table to obtain the row labels for
	 * @return an array holding the row labels
	 */
	public static String[] getTableColumnHeaders(ImRegion table) {
		if (table.getPage() == null)
			return null;
		
		//	get cells
		ImRegion[][] cells = getTableCells(table, null, null);
		if (cells == null)
			return null;
		
		//	return column headers
		return getTableColumnHeaders(cells, table.getPage());
	}
	
	private static String[] getTableColumnHeaders(ImRegion[][] cells, ImPage page) {
		String[] colHeaders = new String[cells[0].length];
		for (int c = 0; c < cells[0].length; c++) {
			if ((c != 0) && (cells[0][c] == cells[0][c-1])) {
				colHeaders[c] = colHeaders[c-1];
//				continue;
			}
//			ImWord[] words;
//			if (cells[0][c].getPage() == null)
//				words = page.getWordsInside(cells[0][c].bounds);
//			else words = cells[0][c].getWords();
//			if (words.length == 0)
//				colHeaders[c] = "";
//			else {
//				Arrays.sort(words, textStreamOrder);
//				colHeaders[c] = getString(words[0], words[words.length-1], true);
//			}
			else colHeaders[c] = getCellString(cells[0][c], page);
		}
		return colHeaders;
	}
	
	/**
	 * Connect two tables atop one another, conceptually concatenating their
	 * columns. If the rows of the argument tables are connected to other
	 * tables, the connected tables must be compatible as well, and if so,
	 * their columns are also connected.
	 * @param topTable the upper table
	 * @param topTable the lower table
	 * @return true if the connection was successful, false otherwise
	 */
	public static boolean connectTableColumns(ImRegion topTable, ImRegion bottomTable) {
		ImRegion[] topTables = getRowConnectedTables(topTable);
		ImRegion[] bottomTables = getRowConnectedTables(bottomTable);
		if (topTables.length != bottomTables.length)
			return false;
		for (int t = 0; t < topTables.length; t++) {
			if (!areTableColumnsCompatible(topTables[t], bottomTables[t]))
				return false;
		}
		for (int t = 0; t < topTables.length; t++) {
			topTables[t].setAttribute("colsContinueIn", (bottomTables[t].pageId + "." + bottomTables[t].bounds));
			bottomTables[t].setAttribute("colsContinueFrom", (topTables[t].pageId + "." + topTables[t].bounds));
		}
		return true;
	}
	
	/**
	 * Retrieve a table by its ID from an Image Markup document. The ID has the
	 * form '&lt;pageId&gt;.&lt;boundingBox&gt;', as used by the
	 * <code>connectTableRows()</code> and  <code>connectTableColumns()</code>
	 * methods.
	 * @param doc the document to retrieve the table from
	 * @param id the table ID
	 * @return the region representing the table with the argument ID
	 */
	public static ImRegion getTableForId(ImDocument doc, String id) {
		return getRegionForId(doc, ImRegion.TABLE_TYPE, id);
	}
	
	/**
	 * Retrieve a region from an Image Markup document by its type and ID. The
	 * ID has the form '&lt;pageId&gt;.&lt;boundingBox&gt;'.
	 * @param doc the document to retrieve the region from
	 * @param type the region type
	 * @param id the region ID
	 * @return the region with the argument ID
	 */
	public static ImRegion getRegionForId(ImDocument doc, String type, String id) {
		if (id == null)
			return null;
		if (!id.matches("[0-9]+\\.\\[[0-9]+\\,[0-9]+\\,[0-9]+\\,[0-9]+\\]"))
			return null;
		ImPage page = doc.getPage(Integer.parseInt(id.substring(0, id.indexOf('.'))));
		if (page == null)
			return null;
		ImRegion[] pageRegions = page.getRegions(type);
		for (int r = 0; r < pageRegions.length; r++) {
			if (id.endsWith("." + pageRegions[r].bounds))
				return pageRegions[r];
		}
		return null;
	}
	
	/**
	 * Retrieve the tables whose rows are connected to the rows of the argument
	 * table, directly or transitively. If the rows of the argument table are
	 * not connected to other tables, the returned array holds the argument
	 * table as its only element.
	 * @param table the tables whose connected tables to find
	 * @return an array holding the connected tables, in connection order
	 */
	public static ImRegion[] getRowConnectedTables(ImRegion table) {
		LinkedList tables = new LinkedList();
		HashSet distinctTables = new HashSet();
		distinctTables.add(table);
//		System.out.println("Table is " + table.bounds + ", rows continue from " + table.getAttribute("rowsContinueFrom"));
		for (ImRegion pTable = getTableForId(table.getDocument(), ((String) table.getAttribute("rowsContinueFrom"))); pTable != null; pTable = getTableForId(pTable.getDocument(), ((String) pTable.getAttribute("rowsContinueFrom")))) {
//			System.out.println(" got predecessor " + pTable.bounds + ", rows continue from " + pTable.getAttribute("rowsContinueFrom"));
			if (distinctTables.add(pTable))
				tables.addFirst(pTable);
			else break;
		}
		tables.add(table);
//		System.out.println("Table is " + table.bounds + ", rows continue in " + table.getAttribute("rowsContinueIn"));
		for (ImRegion sTable = getTableForId(table.getDocument(), ((String) table.getAttribute("rowsContinueIn"))); sTable != null; sTable = getTableForId(sTable.getDocument(), ((String) sTable.getAttribute("rowsContinueIn")))) {
//			System.out.println(" got successor " + sTable.bounds + ", rows continue in " + sTable.getAttribute("rowsContinueIn"));
			if (distinctTables.add(sTable))
				tables.addLast(sTable);
			else break;
		}
		return ((ImRegion[]) tables.toArray(new ImRegion[tables.size()]));
	}
	
	/**
	 * Retrieve the tables whose columns are connected to the columns of the
	 * argument table, directly or transitively. If the columns of the argument
	 * table are not connected to other tables, the returned array holds the
	 * argument table as its only element.
	 * @param table the tables whose connected tables to find
	 * @return an array holding the connected tables, in connection order
	 */
	public static ImRegion[] getColumnConnectedTables(ImRegion table) {
		LinkedList tables = new LinkedList();
		HashSet distinctTables = new HashSet();
		distinctTables.add(table);
//		System.out.println("Table is " + table.bounds + ", columns continue from " + table.getAttribute("colsContinueFrom"));
		for (ImRegion pTable = getTableForId(table.getDocument(), ((String) table.getAttribute("colsContinueFrom"))); pTable != null; pTable = getTableForId(pTable.getDocument(), ((String) pTable.getAttribute("colsContinueFrom")))) {
//			System.out.println(" got predecessor " + pTable.bounds + ", columns continue from " + pTable.getAttribute("colsContinueFrom"));
			if (distinctTables.add(pTable))
				tables.addFirst(pTable);
			else break;
		}
		tables.add(table);
//		System.out.println("Table is " + table.bounds + ", columns continue in " + table.getAttribute("colsContinueIn"));
		for (ImRegion sTable = getTableForId(table.getDocument(), ((String) table.getAttribute("colsContinueIn"))); sTable != null; sTable = getTableForId(sTable.getDocument(), ((String) sTable.getAttribute("colsContinueIn")))) {
//			System.out.println(" got successor " + sTable.bounds + ", columns continue in " + sTable.getAttribute("colsContinueIn"));
			if (distinctTables.add(sTable))
				tables.addLast(sTable);
			else break;
		}
		return ((ImRegion[]) tables.toArray(new ImRegion[tables.size()]));
	}
	
	/**
	 * Retrieve the tables whose rows or columns are connected to the rows or
	 * columns of the argument table, directly or transitively. The outer
	 * dimension of the returned array is top-down, the inner left-right.
	 * @param table the tables whose connected tables to find
	 * @return an array holding the connected tables, in connection order
	 */
	public static ImRegion[][] getConnectedTables(ImRegion table) {
		ImRegion[] colConTables = getColumnConnectedTables(table);
//		System.out.println("Got " + colConTables.length + " column connected tables");
		ImRegion[][] conTables = new ImRegion[colConTables.length][];
		for (int t = 0; t < colConTables.length; t++)
			conTables[t] = getRowConnectedTables(colConTables[t]);
//		System.out.println("Got " + conTables[0].length + " row connected tables");
		return conTables;
	}
	
	/**
	 * Retrieve the table colmn regions connected to a given table column
	 * region in a grid of column connected tables.
	 * @param table the table the argument column region lies in
	 * @param column the column region whose counterparts to find
	 * @return an array holding the table column regions
	 */
	public static ImRegion[] getConnectedTableColumns(ImRegion table, ImRegion column) {
		ImRegion[] tables = getColumnConnectedTables(table);
		return getConnectedTableColumns(table, tables, column);
	}
	private static ImRegion[] getConnectedTableColumns(ImRegion table, ImRegion[] tables, ImRegion column) {
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			ImRegion[] conCols = {column};
			return conCols;
		}
		
		//	compute table-relative split area
		int relToSplitColLeft = (column.bounds.left - table.bounds.left);
		int relToSplitColRight = (column.bounds.right - table.bounds.left);
		
		//	get corresponding columns in connected tables
		ImRegion[] conCols = new ImRegion[tables.length];
		for (int t = 0; t < tables.length; t++) {
			
			//	this one's clear
			if (tables[t] == table) {
				conCols[t] = column;
				continue;
			}
			
			//	collect rows inside table-relative merge area
			ImRegion[] tableCols = getTableColumns(tables[t]);
			for (int c = 0; c < tableCols.length; c++) {
				int relColCenter = (((tableCols[c].bounds.left + tableCols[c].bounds.right) / 2) - tables[t].bounds.left);
				if ((relToSplitColLeft < relColCenter) && (relColCenter < relToSplitColRight)) {
					if (conCols[t] == null)
						conCols[t] = tableCols[c];
					else return null;
				}
			}
			if (conCols[t] == null)
				return null;
		}
		
		//	finally ...
		return conCols;
	}
	
	/**
	 * Retrieve the table colmn regions connected to some given table column
	 * regions in a grid of column connected tables. Column connected tables
	 * represent the outer dimension in the returned array.
	 * @param table the table the argument column region lies in
	 * @param columns the column regions whose counterparts to find
	 * @return an array holding the table column regions
	 */
	public static ImRegion[][] getConnectedTableColumns(ImRegion table, ImRegion[] columns) {
		ImRegion[] tables = getColumnConnectedTables(table);
		return getConnectedTableColumns(table, tables, columns);
	}
	private static ImRegion[][] getConnectedTableColumns(ImRegion table, ImRegion[] tables, ImRegion[] columns) {
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			ImRegion[][] conCols = {columns};
			return conCols;
		}
		
		//	compute merge area
		ImRegion[] tableCols = getTableColumns(table);
		Arrays.sort(tableCols, leftRightOrder);
		HashSet colSet = new HashSet(Arrays.asList(columns));
		int firstColIndex = -1;
		for (int c = 0; c < tableCols.length; c++)
			if (colSet.contains(tableCols[c])) {
				firstColIndex = c;
				break;
			}
		int tableColCount = tableCols.length;
		
		//	get corresponding columns in connected tables
		ImRegion[][] conCols = new ImRegion[tables.length][];
		for (int t = 0; t < tables.length; t++) {
			
			//	this one's clear
			if (tables[t] == table) {
				conCols[t] = columns;
				continue;
			}
			
			//	collect columns inside merge area 
			tableCols = getTableColumns(tables[t]);
			if (tableCols.length != tableColCount)
				return null;
			Arrays.sort(tableCols, leftRightOrder);
			conCols[t] = new ImRegion[columns.length];
			System.arraycopy(tableCols, firstColIndex, conCols[t], 0, columns.length);
		}
		
		//	finally ...
		return conCols;
	}
	
	/**
	 * Retrieve the table row regions connected to a given table row region in
	 * a grid of row connected tables.
	 * @param table the table the argument row region lies in
	 * @param row the row region whose counterparts to find
	 * @return an array holding the table row regions
	 */
	public static ImRegion[] getConnectedTableRows(ImRegion table, ImRegion row) {
		ImRegion[] tables = getRowConnectedTables(table);
		return getConnectedTableRows(table, tables, row);
	}
	private static ImRegion[] getConnectedTableRows(ImRegion table, ImRegion[] tables, ImRegion row) {
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			ImRegion[] conRows = {row};
			return conRows;
		}
		
		//	compute table-relative split area
		int relRowTop = (row.bounds.top - table.bounds.top);
		int relRowBottom = (row.bounds.bottom - table.bounds.top);
		
		//	get corresponding rows in connected tables
		ImRegion[] conRows = new ImRegion[tables.length];
		for (int t = 0; t < tables.length; t++) {
			
			//	this one's clear
			if (tables[t] == table) {
				conRows[t] = row;
				continue;
			}
			
			//	collect rows inside table-relative merge area
			ImRegion[] tableRows = getTableRows(tables[t]);
			for (int r = 0; r < tableRows.length; r++) {
				int relRowCenter = (((tableRows[r].bounds.top + tableRows[r].bounds.bottom) / 2) - tables[t].bounds.top);
				System.out.println("   - row " + tableRows[r].bounds + ", relative center is " + relRowCenter);
				if ((relRowTop < relRowCenter) && (relRowCenter < relRowBottom)) {
					if (conRows[t] == null)
						conRows[t] = tableRows[r];
					else return null;
				}
			}
			if (conRows[t] == null)
				return null;
		}
		
		//	finally ...
		return conRows;
	}
	
	/**
	 * Retrieve the table row regions connected to some given table row regions
	 * in a grid of row connected tables. Row connected tables represent the
	 * outer dimension in the returned array.
	 * @param table the table the argument row region lies in
	 * @param rows the row regions whose counterparts to find
	 * @return an array holding the table row regions
	 */
	public static ImRegion[][] getConnectedTableRows(ImRegion table, ImRegion[] rows) {
		ImRegion[] tables = getRowConnectedTables(table);
		return getConnectedTableRows(table, tables, rows);
	}
	private static ImRegion[][] getConnectedTableRows(ImRegion table, ImRegion[] tables, ImRegion[] rows) {
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			ImRegion[][] conRows = {rows};
			return conRows;
		}
		
		//	compute merge area
		ImRegion[] tableRows = getTableRows(table);
		HashSet rowSet = new HashSet(Arrays.asList(rows));
		Arrays.sort(tableRows, topDownOrder);
		int firstRowIndex = -1;
		for (int r = 0; r < tableRows.length; r++)
			if (rowSet.contains(tableRows[r])) {
				firstRowIndex = r;
				break;
			}
		int tableRowCount = tableRows.length;
		
		//	get corresponding rows in connected tables
		ImRegion[][] conRows = new ImRegion[tables.length][];
		for (int t = 0; t < tables.length; t++) {
			//	this one's clear
			if (tables[t] == table) {
				conRows[t] = rows;
				continue;
			}
			
			//	collect rows inside merge area 
			tableRows = getTableRows(tables[t]);
			if (tableRows.length != tableRowCount)
				return null;
			Arrays.sort(tableRows, topDownOrder);
			conRows[t] = new ImRegion[rows.length];
			System.arraycopy(tableRows, firstRowIndex, conRows[t], 0, rows.length);
		}
		
		//	finally ...
		return conRows;
	}
	
	/**
	 * Merge up a aeries of adjacent table columns. If the columns of the
	 * argument table are connected to the columns in other tables, the
	 * corresponding columns of the latter tables are merged as well.
	 * @param table the table the columns belong to
	 * @param mergeColumns the columns to merge
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean mergeTableColumns(ImRegion table, ImRegion[] mergeColumns, Properties conflictResolutionOperations) {
		ImRegion[] tables = getColumnConnectedTables(table);
		
		//	get connected columns
		ImRegion[][] mergeTableCols = getConnectedTableColumns(table, tables, mergeColumns);
		if (mergeTableCols == null)
			return false;
		
		//	perform actual merge
		for (int t = 0; t < tables.length; t++)
			doMergeTableColumns(tables[t].getPage(), tables[t], mergeTableCols[t], conflictResolutionOperations);
		return true;
	}
	
	private static void doMergeTableColumns(ImPage page, ImRegion table, ImRegion[] mergeCols, Properties conflictResolutionOperations) {
		
		//	mark merged column
		Arrays.sort(mergeCols, leftRightOrder);
		ImRegion mCol = new ImRegion(page, ImLayoutObject.getAggregateBox(mergeCols), ImRegion.TABLE_COL_TYPE);
		
		//	preserve row label marker (via majority vote on width)
		int colWidthSum = 0;
		int labelColWidthSum = 0;
		for (int c = 0; c < mergeCols.length; c++) {
			colWidthSum += mergeCols[c].bounds.getWidth();
			if (mergeCols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				labelColWidthSum += mergeCols[c].bounds.getWidth();
		}
		if (colWidthSum < (labelColWidthSum * 2))
			mCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		
		//	collect cells in merged columns
		ImRegion[] mColRows = getRegionsOverlapping(page, mCol.bounds, ImRegion.TABLE_ROW_TYPE);
		Arrays.sort(mColRows, topDownOrder);
		ImRegion[] mColCells = getRegionsOverlapping(page, mCol.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(mColCells, topDownOrder);
		ImRegion[][] mergeColCells = new ImRegion[mColRows.length][mergeCols.length];
		for (int r = 0; r < mColRows.length; r++)
			for (int c = 0; c < mergeCols.length; c++) {
				for (int l = 0; l < mColCells.length; l++)
					if (mColCells[l].bounds.overlaps(mColRows[r].bounds) && mColCells[l].bounds.overlaps(mergeCols[c].bounds)) {
						mergeColCells[r][c] = mColCells[l];
						break;
					}
				//	TODO make this nested loop join faster, somehow
			}
		
		//	create merged cells row-wise
		for (int r = 0; r < mColRows.length; r++) {
			int lr = r;
			
			//	find row with straight bottom
			while ((lr+1) < mColRows.length) {
				boolean rowClosed = true;
				for (int c = 0; c < mergeCols.length; c++)
					if ((mergeColCells[lr][c] != null) && mergeColCells[lr][c].bounds.overlaps(mColRows[lr+1].bounds)) {
						rowClosed = false;
						break;
					}
				if (rowClosed)
					break;
				else lr++;
			}
			
			//	aggregate merged cell bounds
			int mCellLeft = mCol.bounds.left;
			int mCellRight = mCol.bounds.right;
			for (int ar = r; ar <= lr; ar++) {
				for (int c = 0; c < mergeCols.length; c++)
					if (mergeColCells[ar][c] != null){
						mCellLeft = Math.min(mCellLeft, mergeColCells[ar][c].bounds.left);
						mCellRight = Math.max(mCellRight, mergeColCells[ar][c].bounds.right);
					}
			}
			BoundingBox mCellBounds = new BoundingBox(mCellLeft, mCellRight, mColRows[r].bounds.top, mColRows[lr].bounds.bottom);
			
			//	remove merged cells
			ImRegion[] mCellCells = getRegionsOverlapping(page, mCellBounds, ImRegion.TABLE_CELL_TYPE);
			for (int c = 0; c < mCellCells.length; c++)
				page.removeRegion(mCellCells[c]);
			
			//	add merged cell
			markTableCell(page, mCellBounds, true, false);
			
			//	jump to last row (loop increment will switch one further)
			r = lr;
		}
		
		//	remove merged columns
		for (int c = 0; c < mergeCols.length; c++)
			page.removeRegion(mergeCols[c]);
		
		//	update cells (whichever we might have cut apart in columns to left or right of merger)
		ImRegion[][] tableCells = markTableCells(page, table);
		
		//	clean up table structure
		orderTableWords(tableCells);
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
	}
	
	private static final boolean DEBUG_SPLIT_COLS = true;
	
	/**
	 * Split a table column. Depending on the splitting box, the argument table
	 * column is split into two (splitting off left or right) or three (splitting
	 * down the middle) new columns. If the columns of the argument table are
	 * connected to the columns in other tables, the corresponding columns of the
	 * latter tables are split as well.
	 * @param page the page the table lies upon
	 * @param table the table the column belongs to
	 * @param toSplitColumn the table column to split
	 * @param splitBox the box defining where to split
	 * @param normSpaceWidth the width of a normal space between two words, as
	 *            a fraction of word height
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean splitTableColumn(ImPage page, ImRegion table, ImRegion toSplitColumn, BoundingBox splitBox, ImWord[] tableWords, float normSpaceWidth, Properties conflictResolutionOperations) {
		return splitTableColumn(page, table, toSplitColumn, splitBox, true, tableWords, normSpaceWidth, conflictResolutionOperations);
	}
	
	/**
	 * Split a table column. Depending on the splitting box, the argument table
	 * column is split into two (splitting off left or right) or three (splitting
	 * down the middle) new columns. If the columns of the argument table are
	 * connected to the columns in other tables, the corresponding columns of the
	 * latter tables are split as well.
	 * @param page the page the table lies upon
	 * @param table the table the column belongs to
	 * @param toSplitColumn the table column to split
	 * @param splitBox the box defining where to split
	 * @param fuzzy adjust exact split location(s) to words contained in column?
	 * @param normSpaceWidth the width of a normal space between two words, as
	 *            a fraction of word height
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean splitTableColumn(ImPage page, ImRegion table, ImRegion toSplitColumn, BoundingBox splitBox, boolean fuzzy, ImWord[] tableWords, float normSpaceWidth, Properties conflictResolutionOperations) {
		if (DEBUG_SPLIT_COLS) System.out.println("Splitting table column at " + toSplitColumn.pageId + "." + toSplitColumn.bounds + " along " + splitBox);
		ImRegion[] tables = getColumnConnectedTables(table);
		if (DEBUG_SPLIT_COLS) System.out.println(" - got " + tables.length + " column connected tables");
		
		//	compute table area statistics if not given
		if (tableWords == null)
			tableWords = table.getWords();
		
		//	aggregate words to blocks, bridging spaces to prevent mistaking them for column gaps
		WordBlock[] toSplitColWords = getWordBlocksForColSplit(tableWords, toSplitColumn.bounds, normSpaceWidth);
		if (DEBUG_SPLIT_COLS) System.out.println(" - got " + toSplitColWords.length + " word blocks at normalized space width " + normSpaceWidth);
		
		//	compute relative split bounds
		int relLeftSplitRight = 0;
		int relSplitLeft = (splitBox.right - toSplitColumn.bounds.left);
		int relSplitRight = (splitBox.left - toSplitColumn.bounds.left);
		int relRightSplitLeft = toSplitColumn.bounds.getWidth();
		HashSet multiColWords = null;
		
		//	order words mainly left of, inside, and right of selection
		if (fuzzy) {
			for (int w = 0; w < toSplitColWords.length; w++) {
				if (toSplitColWords[w].centerX < splitBox.left)
					relLeftSplitRight = Math.max(relLeftSplitRight, (toSplitColWords[w].bounds.right - toSplitColumn.bounds.left));
				else if (toSplitColWords[w].centerX < splitBox.right) {
					relSplitLeft = Math.min(relSplitLeft, (toSplitColWords[w].bounds.left - toSplitColumn.bounds.left));
					relSplitRight = Math.max(relSplitRight, (toSplitColWords[w].bounds.right - toSplitColumn.bounds.left));
				}
				else relRightSplitLeft = Math.min(relRightSplitLeft, (toSplitColWords[w].bounds.left - toSplitColumn.bounds.left));
			}
			if (DEBUG_SPLIT_COLS) System.out.println(" - sorted into relative intervals [0," + relLeftSplitRight + "], [" + relSplitLeft + "," + relSplitRight + "], and [" + relRightSplitLeft + "," + toSplitColumn.bounds.getWidth() + "]");
		}
		
		//	order words strictly left of, inside, and right of selection, and collect any others as column spanning words
		else {
			multiColWords = new HashSet();
			for (int w = 0; w < toSplitColWords.length; w++) {
				if (toSplitColWords[w].bounds.right <= splitBox.left)
					relLeftSplitRight = Math.max(relLeftSplitRight, (toSplitColWords[w].bounds.right - toSplitColumn.bounds.left));
				else if (splitBox.right <= toSplitColWords[w].bounds.left)
					relRightSplitLeft = Math.min(relRightSplitLeft, (toSplitColWords[w].bounds.left - toSplitColumn.bounds.left));
				else if (toSplitColWords[w].bounds.left < splitBox.left) // protruding beyong split box on left
					multiColWords.add(toSplitColWords[w]);
				else if (splitBox.right < toSplitColWords[w].bounds.right) // protruding beyond split box on right
					multiColWords.add(toSplitColWords[w]);
				else {
					relSplitLeft = Math.min(relSplitLeft, (toSplitColWords[w].bounds.left - toSplitColumn.bounds.left));
					relSplitRight = Math.max(relSplitRight, (toSplitColWords[w].bounds.right - toSplitColumn.bounds.left));
				}
			}
			if (DEBUG_SPLIT_COLS) {
				System.out.println(" - sorted into relative intervals [0," + relLeftSplitRight + "], [" + relSplitLeft + "," + relSplitRight + "], and [" + relRightSplitLeft + "," + toSplitColumn.bounds.getWidth() + "]");
				System.out.println(" - collected " + multiColWords.size() + " word blocks (of " + toSplitColWords.length + ") spanning split columns");
			}
		}
		
		//	anything to split at all?
		int emptySplitCols = 0;
		if (relLeftSplitRight == 0)
			emptySplitCols++;
		if (relSplitRight <= relSplitLeft)
			emptySplitCols++;
		if (relRightSplitLeft == toSplitColumn.bounds.getWidth())
			emptySplitCols++;
		if (emptySplitCols >= 2) {
			if (DEBUG_SPLIT_COLS) System.out.println("   ==> cannot split with two empty intervals");
			return false;
		}
		
		//	check if we have words obstructing the split (only in fuzzy mode, strict mode collects them above)
		if (fuzzy && ((relSplitLeft < relLeftSplitRight) || (relRightSplitLeft < relSplitRight))) {
			
			//	determine right edge of all words _completely_ to left of selection ...
			//	... and left edge of all words _completely_ to right of selection ...
			int leftSplitRight = toSplitColumn.bounds.left;
			int rightSplitLeft = toSplitColumn.bounds.right;
			ArrayList insideSplitWords = new ArrayList();
			for (int w = 0; w < toSplitColWords.length; w++) {
				if (toSplitColWords[w].bounds.right <= splitBox.left)
					leftSplitRight = Math.max(leftSplitRight, toSplitColWords[w].bounds.right);
				else if (splitBox.right <= toSplitColWords[w].bounds.left)
					rightSplitLeft = Math.min(rightSplitLeft, toSplitColWords[w].bounds.left);
				else insideSplitWords.add(toSplitColWords[w]);
			}
			
			//	mark all remaining words that protrude beyond (or even only close to ???) either of these edges as multi-column
			//	... and determine boundaries of middle column from the rest
			//	TODO depending on average column gap, be even more sensitive about multi-column cells ...
			//	... as they might end up failing to completely reach edge of neighbor column and only get close
			int splitLeft = splitBox.right;
			int splitRight = splitBox.left;
			multiColWords = new HashSet();
			for ( int w = 0; w < insideSplitWords.size(); w++) {
				WordBlock isWord = ((WordBlock) insideSplitWords.get(w));
				if ((toSplitColumn.bounds.left < leftSplitRight) && (isWord.bounds.left <= leftSplitRight))
					multiColWords.add(isWord);
				else if ((rightSplitLeft < toSplitColumn.bounds.right) && (rightSplitLeft <= isWord.bounds.right))
					multiColWords.add(isWord);
				else {
					splitLeft = Math.min(splitLeft, isWord.bounds.left);
					splitRight = Math.max(splitRight, isWord.bounds.right);
				}
			}
			
			//	re-compute relative split bounds
			relLeftSplitRight = (leftSplitRight - toSplitColumn.bounds.left);
			relSplitLeft = (splitLeft - toSplitColumn.bounds.left);
			relSplitRight = (splitRight - toSplitColumn.bounds.left);
			relRightSplitLeft = (rightSplitLeft - toSplitColumn.bounds.left);
			if (DEBUG_SPLIT_COLS) {
				System.out.println(" - overlap resolved to relative intervals [0," + relLeftSplitRight + "], [" + relSplitLeft + "," + relSplitRight + "], and [" + relRightSplitLeft + "," + toSplitColumn.bounds.getWidth() + "]");
				System.out.println(" - collected " + multiColWords.size() + " word blocks (of " + toSplitColWords.length + ") spanning split columns");
			}
		}
		
		//	left side of split empty ==> move out left edge of middle
		if (relLeftSplitRight == 0)
			relSplitLeft = 0;
		
		//	right side of split empty ==> move out right edge of middle
		if (relRightSplitLeft == toSplitColumn.bounds.getWidth())
			relSplitRight = toSplitColumn.bounds.getWidth();
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			doSplitTableColumn(table.getPage(), table, toSplitColumn, toSplitColWords, multiColWords, relLeftSplitRight, relSplitLeft, relSplitRight, relRightSplitLeft, conflictResolutionOperations);
			if (DEBUG_SPLIT_COLS) System.out.println(" ==> single table column split done");
			return true;
		}
		
		//	get all connected columns
		ImRegion[] toSplitTableCols = getConnectedTableColumns(table, tables, toSplitColumn);
		if (toSplitTableCols == null) {
			if (DEBUG_SPLIT_COLS) System.out.println(" ==> failed to get connected columns for all tables");
			return false;
		}
		
		//	perform split
		float relLeftMiddleSplit = (((float) (relLeftSplitRight + relSplitLeft)) / (2 * (toSplitColumn.bounds.right - toSplitColumn.bounds.left)));
		float relMiddleRightSplit = (((float) (relSplitRight + relRightSplitLeft)) / (2 * (toSplitColumn.bounds.right - toSplitColumn.bounds.left)));
		if (DEBUG_SPLIT_COLS) System.out.println(" - splitting at relative positions " + relLeftMiddleSplit + " and " + relMiddleRightSplit);
		for (int t = 0; t < tables.length; t++) {
			if (DEBUG_SPLIT_COLS) System.out.println(" - splitting column in table at " + tables[t].pageId + "." + tables[t].bounds);
			if (tables[t] == table)
				doSplitTableColumn(tables[t].getPage(), tables[t], toSplitTableCols[t], toSplitColWords, multiColWords, relLeftSplitRight, relSplitLeft, relSplitRight, relRightSplitLeft, conflictResolutionOperations);
			else doSplitTableColumn(tables[t], toSplitTableCols[t], relLeftMiddleSplit, relMiddleRightSplit, fuzzy, normSpaceWidth, conflictResolutionOperations);
		}
		if (DEBUG_SPLIT_COLS) System.out.println(" ==> multi table column split done");
		return true;
	}
	
	private static void doSplitTableColumn(ImRegion table, ImRegion toSplitCol, float relLeftMiddleSplit, float relMiddleRightSplit, boolean fuzzy, float normSpaceWidth, Properties conflictResolutionOperations) {
		
		//	compute relative split box
		int splitBoxLeft = (((int) (relLeftMiddleSplit * (toSplitCol.bounds.right - toSplitCol.bounds.left))) + toSplitCol.bounds.left);
		int splitBoxRight = (((int) (relMiddleRightSplit * (toSplitCol.bounds.right - toSplitCol.bounds.left))) + toSplitCol.bounds.left);
		
		//	aggregate words to blocks, bridging spaces to prevent mistaking them for column gaps
		WordBlock[] toSplitColWords = getWordBlocksForColSplit(table.getWords(), toSplitCol.bounds, normSpaceWidth);
		
		//	compute relative split bounds
		int relLeftSplitRight = 0;
		int relSplitLeft = (splitBoxRight - toSplitCol.bounds.left);
		int relSplitRight = (splitBoxLeft - toSplitCol.bounds.left);
		int relRightSplitLeft = (toSplitCol.bounds.right - toSplitCol.bounds.left);
		HashSet multiColWords = null;
		
		//	order words mainly left of, inside, and right of selection
		if (fuzzy) {
			for (int w = 0; w < toSplitColWords.length; w++) {
				if (toSplitColWords[w].centerX < splitBoxLeft)
					relLeftSplitRight = Math.max(relLeftSplitRight, (toSplitColWords[w].bounds.right - toSplitCol.bounds.left));
				else if (toSplitColWords[w].centerX < splitBoxRight) {
					relSplitLeft = Math.min(relSplitLeft, (toSplitColWords[w].bounds.left - toSplitCol.bounds.left));
					relSplitRight = Math.max(relSplitRight, (toSplitColWords[w].bounds.right - toSplitCol.bounds.left));
				}
				else relRightSplitLeft = Math.min(relRightSplitLeft, (toSplitColWords[w].bounds.left - toSplitCol.bounds.left));
			}
			
			//	check if we have words obstructing the split
			if ((relSplitLeft < relLeftSplitRight) || (relRightSplitLeft < relSplitRight)) {
				
				//	determine right edge of all words _completely_ to left of selection ...
				//	... and left edge of all words _completely_ to right of selection ...
				int leftSplitRight = toSplitCol.bounds.left;
				int rightSplitLeft = toSplitCol.bounds.right;
				ArrayList insideSplitWords = new ArrayList();
				for (int w = 0; w < toSplitColWords.length; w++) {
					if (toSplitColWords[w].bounds.right <= splitBoxLeft)
						leftSplitRight = Math.max(leftSplitRight, toSplitColWords[w].bounds.right);
					else if (splitBoxRight <= toSplitColWords[w].bounds.left)
						rightSplitLeft = Math.min(rightSplitLeft, toSplitColWords[w].bounds.left);
					else insideSplitWords.add(toSplitColWords[w]);
				}
				
				//	mark all remaining words that protrude beyond either of these edges as multi-column
				//	... and determine boundaries of middle column from the rest
				//	TODO depending on average column gap, be even more sensitive about multi-column cells ...
				//	... as they might end up failing to completely reach edge of neighbor column and only get close
				int splitLeft = splitBoxRight;
				int splitRight = splitBoxLeft;
				multiColWords = new HashSet();
				for ( int w = 0; w < insideSplitWords.size(); w++) {
					WordBlock isWord = ((WordBlock) insideSplitWords.get(w));
					if ((toSplitCol.bounds.left < leftSplitRight) && (isWord.bounds.left <= leftSplitRight))
						multiColWords.add(isWord);
					else if ((rightSplitLeft < toSplitCol.bounds.right) && (rightSplitLeft <= isWord.bounds.right))
						multiColWords.add(isWord);
					else {
						splitLeft = Math.min(splitLeft, isWord.bounds.left);
						splitRight = Math.max(splitRight, isWord.bounds.right);
					}
				}
				
				//	re-compute relative split bounds
				relLeftSplitRight = (leftSplitRight - toSplitCol.bounds.left);
				relSplitLeft = (splitLeft - toSplitCol.bounds.left);
				relSplitRight = (splitRight - toSplitCol.bounds.left);
				relRightSplitLeft = (rightSplitLeft - toSplitCol.bounds.left);
			}
		}
		
		//	order words strictly left of, inside, and right of selection, and collect any others as column spanning words
		else {
			multiColWords = new HashSet();
			for (int w = 0; w < toSplitColWords.length; w++) {
				if (toSplitColWords[w].bounds.right <= splitBoxLeft)
					relLeftSplitRight = Math.max(relLeftSplitRight, (toSplitColWords[w].bounds.right - toSplitCol.bounds.left));
				else if (splitBoxRight <= toSplitColWords[w].bounds.left)
					relRightSplitLeft = Math.min(relRightSplitLeft, (toSplitColWords[w].bounds.left - toSplitCol.bounds.left));
				else if (toSplitColWords[w].bounds.left < splitBoxLeft) // protruding beyong split box on left
					multiColWords.add(toSplitColWords[w]);
				else if (splitBoxRight < toSplitColWords[w].bounds.right) // protruding beyond split box on right
					multiColWords.add(toSplitColWords[w]);
				else {
					relSplitLeft = Math.min(relSplitLeft, (toSplitColWords[w].bounds.left - toSplitCol.bounds.left));
					relSplitRight = Math.max(relSplitRight, (toSplitColWords[w].bounds.right - toSplitCol.bounds.left));
				}
			}
		}
		
		//	do split with case adjusted absolute numbers
		doSplitTableColumn(table.getPage(), table, toSplitCol, toSplitColWords, multiColWords, relLeftSplitRight, relSplitLeft, relSplitRight, relRightSplitLeft, conflictResolutionOperations);
	}
	
	private static void doSplitTableColumn(ImPage page, ImRegion table, ImRegion toSplitCol, WordBlock[] toSplitColWords, HashSet multiColWords, int relLeftSplitRight, int relSplitLeft, int relSplitRight, int relRightSplitLeft, Properties conflictResolutionOperations) {
		
		//	create two or three new columns
		if (0 < relLeftSplitRight) {
			BoundingBox lcBox = new BoundingBox(toSplitCol.bounds.left, (toSplitCol.bounds.left + relLeftSplitRight), table.bounds.top, table.bounds.bottom);
			ImRegion sCol = new ImRegion(table.getPage(), lcBox, ImRegion.TABLE_COL_TYPE);
			if (toSplitCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		if (relSplitLeft < relSplitRight) {
			BoundingBox icBox = new BoundingBox((toSplitCol.bounds.left + relSplitLeft), (toSplitCol.bounds.left + relSplitRight), table.bounds.top, table.bounds.bottom);
			ImRegion sCol = new ImRegion(table.getPage(), icBox, ImRegion.TABLE_COL_TYPE);
			if (toSplitCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		if (relRightSplitLeft < (toSplitCol.bounds.right - toSplitCol.bounds.left)) {
			BoundingBox rcBox = new BoundingBox((toSplitCol.bounds.left + relRightSplitLeft), toSplitCol.bounds.right, table.bounds.top, table.bounds.bottom);
			ImRegion sCol = new ImRegion(table.getPage(), rcBox, ImRegion.TABLE_COL_TYPE);
			if (toSplitCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		
		//	remove selected column
		page.removeRegion(toSplitCol);
		
		//	get split columns and existing cells
		ImRegion[] sCols = page.getRegionsInside(ImRegion.TABLE_COL_TYPE, toSplitCol.bounds, false);
		Arrays.sort(sCols, leftRightOrder);
		ImRegion[] toSplitColCells = getRegionsOverlapping(page, toSplitCol.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(toSplitColCells, topDownOrder);
		
		//	create new cells left to right
		for (int l = 0; l < toSplitColCells.length; l++) {
			
			//	handle base case, saving all the hassle below
			if (multiColWords == null) {
				
				//	create split cells
				for (int c = 0; c < sCols.length; c++) {
					int sCellLeft = ((c == 0) ? toSplitColCells[l].bounds.left : sCols[c].bounds.left);
					int sCellRight = (((c+1) == sCols.length) ? toSplitColCells[l].bounds.right : sCols[c].bounds.right);
					markTableCell(page, new BoundingBox(sCellLeft, sCellRight, toSplitColCells[l].bounds.top, toSplitColCells[l].bounds.bottom), false, true);
				}
				
				//	clean up and we're done here
				page.removeRegion(toSplitColCells[l]);
				continue;
			}
			
			//	check for multi-column cells
			boolean[] cellReachesIntoNextCol = new boolean[sCols.length - 1];
			Arrays.fill(cellReachesIntoNextCol, false);
			for (int c = 0; c < (sCols.length - 1); c++) {
				WordBlock[] sCellWords = getWordBlocksOverlapping(toSplitColWords, new BoundingBox(sCols[c].bounds.left, sCols[c+1].bounds.right, toSplitColCells[l].bounds.top, toSplitColCells[l].bounds.bottom));
				if (sCellWords.length == 0)
					continue;
				Arrays.sort(sCellWords, leftRightOrder);
				for (int w = 0; w < sCellWords.length; w++) {
					if (sCols[c+1].bounds.left < sCellWords[w].bounds.left)
						break; // we're only interested in words overlapping with space between the current pair of columns
					if (multiColWords.contains(sCellWords[w])) {
						cellReachesIntoNextCol[c] = true;
						break; // one word overlapping both columns is enough
					}
				}
			}
			
			//	create cells for split columns
			int sCellLeft = Math.min(toSplitColCells[l].bounds.left, sCols[0].bounds.left);
			for (int c = 0; c < sCols.length; c++) {
				if ((c < cellReachesIntoNextCol.length) && cellReachesIntoNextCol[c])
					continue;
				if ((c+1) == sCols.length)
					markTableCell(page, new BoundingBox(sCellLeft, toSplitColCells[l].bounds.right, toSplitColCells[l].bounds.top, toSplitColCells[l].bounds.bottom), false, true);
				else {
					markTableCell(page, new BoundingBox(sCellLeft, sCols[c].bounds.right, toSplitColCells[l].bounds.top, toSplitColCells[l].bounds.bottom), false, true);
					sCellLeft = sCols[c+1].bounds.left;
				}
			}
			
			//	clean up
			page.removeRegion(toSplitColCells[l]);
		}
		
		//	clean up table structure
		orderTableWords(markTableCells(page, table));
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
	}
	
	private static WordBlock[] getWordBlocksForColSplit(ImWord[] tableWords, BoundingBox bounds, float normSpaceWidth) {
		
		//	get and sort words
		ImWord[] words = getWordsOverlapping(tableWords, bounds);
		sortLeftRightTopDown(words);
		
		//	aggregate words to sequences
		ArrayList wbs = new ArrayList();
		int wbStart = 0;
		for (int w = 1; w <= words.length; w++) {
			if ((w == words.length) || isColumnGap(words[w-1], words[w], normSpaceWidth)) {
				ImWord[] wbWords = new ImWord[w - wbStart];
				System.arraycopy(words, wbStart, wbWords, 0, wbWords.length);
				WordBlock wb = new WordBlock(wbWords[0].getPage(), wbWords, true);
//				System.out.println("Word block: " + wb + " at " + wb.bounds);
				wbs.add(wb);
				wbStart = w;
			}
		}
		
		//	finally ...
		return ((WordBlock[]) wbs.toArray(new WordBlock[wbs.size()]));
	}
	
	private static boolean isColumnGap(ImWord word1, ImWord word2, float normSpaceWidth) {
		if (word1.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
			return true; // explicit end marker
		if (word1.bounds.bottom < word2.centerY)
			return true; // next line
		if (word1.getNextRelation() == ImWord.NEXT_RELATION_CONTINUE)
			return false; // explicit continuation marker
		if (word1.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED)
			return false; // explicit continuation marker
		int lineHeight = ((word1.bounds.getHeight() + word2.bounds.getHeight()) / 2);
		int wordDist = (word2.bounds.left - word1.bounds.right);
//		return ((wordDist * 3) > lineHeight);
		return ((lineHeight * normSpaceWidth) < wordDist);
	}
	
	/**
	 * Merge up a aeries of adjacent table rows. If the rows of the argument
	 * table are connected to the rows in other tables, the corresponding rows
	 * of the latter tables are merged as well.
	 * @param table the table the rows belong to
	 * @param mergeRows the rows to merge
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean mergeTableRows(ImRegion table, ImRegion[] mergeRows, Properties conflictResolutionOperations) {
		ImRegion[] tables = getRowConnectedTables(table);
		
		//	get connected table rows
		ImRegion[][] mergeTableRows = getConnectedTableRows(table, tables, mergeRows);
		if (mergeTableRows == null)
			return false;
		
		//	perform actual merge
		for (int t = 0; t < tables.length; t++)
			doMergeTableRows(tables[t].getPage(), tables[t], mergeTableRows[t], conflictResolutionOperations);
		return true;
	}
	
	private static void doMergeTableRows(ImPage page, ImRegion table, ImRegion[] mergeRows, Properties conflictResolutionOperations) {
		
		//	mark merged row
		Arrays.sort(mergeRows, topDownOrder);
		ImRegion mRow = new ImRegion(page, ImLayoutObject.getAggregateBox(mergeRows), ImRegion.TABLE_ROW_TYPE);
		
		//	preserve column header marker (via majority vote on height)
		int rowHeightSum = 0;
		int headerRowHeightSum = 0;
		for (int r = 0; r < mergeRows.length; r++) {
			rowHeightSum += mergeRows[r].bounds.getHeight();
			if (mergeRows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				headerRowHeightSum += mergeRows[r].bounds.getHeight();
		}
		if (rowHeightSum < (headerRowHeightSum * 2))
			mRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		
		//	collect cells in merged rows
		ImRegion[] mRowCols = getRegionsOverlapping(page, mRow.bounds, ImRegion.TABLE_COL_TYPE);
		Arrays.sort(mRowCols, leftRightOrder);
		ImRegion[] mRowCells = getRegionsOverlapping(page, mRow.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(mRowCells, leftRightOrder);
		BoundingBox[][] mergeColCellBoxes = new BoundingBox[mergeRows.length][mRowCols.length];
		for (int r = 0; r < mergeRows.length; r++)
			for (int c = 0; c < mRowCols.length; c++) {
				for (int l = 0; l < mRowCells.length; l++)
					if (mRowCells[l].bounds.overlaps(mergeRows[r].bounds) && mRowCells[l].bounds.overlaps(mRowCols[c].bounds)) {
						mergeColCellBoxes[r][c] = mRowCells[l].bounds;
						break;
					}
				//	TODO make this nested loop join faster, somehow
			}
		
		//	chop up cell bounding boxes if (a) not all bounding boxes span column gap and (b) no words protrude into area between columns
		for (int r = 0; r < mergeColCellBoxes.length; r++) {
			for (int c = 1; c < mRowCols.length; c++) {
				if (mergeColCellBoxes[r][c-1] != mergeColCellBoxes[r][c])
					continue; // nothing to split here
				boolean allJoined = true;
				for (int cr = 0; cr < mergeColCellBoxes.length; cr++)
					if (mergeColCellBoxes[cr][c-1] != mergeColCellBoxes[cr][c]) {
						allJoined = false;
						break;
					}
				if (allJoined)
					continue; // no point splitting this one up, joined in all rows
				BoundingBox joinedCellBox = new BoundingBox(mRowCols[c-1].bounds.left, mRowCols[c].bounds.right, mergeColCellBoxes[r][c].top, mergeColCellBoxes[r][c].bottom);
				BoundingBox colGapBox = new BoundingBox(mRowCols[c-1].bounds.right, mRowCols[c].bounds.left, mergeColCellBoxes[r][c].top, mergeColCellBoxes[r][c].bottom);
				ImWord[] joinedBoxWords = page.getWordsInside(joinedCellBox);
				boolean keepJoined = false;
				for (int w = 0; w < joinedBoxWords.length; w++)
					if (joinedBoxWords[w].bounds.overlaps(colGapBox)) {
						keepJoined = true;
						break;
					}
				if (keepJoined)
					continue;
				joinedCellBox = mergeColCellBoxes[r][c]; // reuse variable to hold on to actual box from array, which we need to replace
				BoundingBox leftCellBox = new BoundingBox(joinedCellBox.left, mRowCols[c-1].bounds.right, joinedCellBox.top, joinedCellBox.bottom);
				for (int lc = (c-1); lc >= 0; lc--) {
					if (mergeColCellBoxes[r][lc] == joinedCellBox)
						mergeColCellBoxes[r][lc] = leftCellBox;
					else break;
				}
				BoundingBox rightCellBox = new BoundingBox(mRowCols[c].bounds.left, joinedCellBox.right, joinedCellBox.top, joinedCellBox.bottom);
				for (int rc = c; rc < mRowCols.length; rc++) {
					if (mergeColCellBoxes[r][rc] == joinedCellBox)
						mergeColCellBoxes[r][rc] = rightCellBox;
					else break;
				}
			}
		}
		
		//	create merged cells column-wise
		for (int c = 0; c < mRowCols.length; c++) {
			int lc = c;
			
			//	find column with straight right edge
			while ((lc+1) < mRowCols.length) {
				boolean colClosed = true;
				for (int r = 0; r < mergeRows.length; r++)
					if (mergeColCellBoxes[r][lc].overlaps(mRowCols[lc+1].bounds)) {
						colClosed = false;
						break;
					}
				if (colClosed)
					break;
				else lc++;
			}
			
			//	aggregate merged cell bounds
			int mCellTop = mRow.bounds.top;
			int mCellBottom = mRow.bounds.bottom;
			for (int ac = c; ac <= lc; ac++)
				for (int r = 0; r < mergeRows.length; r++) {
					mCellTop = Math.min(mCellTop, mergeColCellBoxes[r][ac].top);
					mCellBottom = Math.max(mCellBottom, mergeColCellBoxes[r][ac].bottom);
				}
			BoundingBox mCellBounds = new BoundingBox(mRowCols[c].bounds.left, mRowCols[lc].bounds.right, mCellTop, mCellBottom);
			
			//	remove merged cells
			ImRegion[] mCellCells = getRegionsOverlapping(page, mCellBounds, ImRegion.TABLE_CELL_TYPE);
			for (int l = 0; l < mCellCells.length; l++)
				page.removeRegion(mCellCells[l]);
			
			//	add merged cell
			markTableCell(page, mCellBounds, true, false);
			
			//	jump to last column (loop increment will switch one further)
			c = lc;
		}
		
		//	remove merged rows
		for (int r = 0; r < mergeRows.length; r++)
			page.removeRegion(mergeRows[r]);
		
		//	update cells (whichever we might have cut apart in rows above or below merger)
		ImRegion[][] tableCells = markTableCells(page, table);
		
		//	clean up table structure
		orderTableWords(tableCells);
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
	}
	
	/**
	 * Split a table row. Depending on the splitting box, the argument table
	 * row is split into two (splitting off top or bottom) or three (splitting
	 * across the middle) new rows. If the rows of the argument table are
	 * connected to the rows in other tables, the corresponding rows of the
	 * latter tables are split as well.
	 * @param table the table the row belongs to
	 * @param toSplitRow the table row to split
	 * @param splitBox the box defining where to split
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean splitTableRow(ImRegion table, ImRegion toSplitRow, BoundingBox splitBox, Properties conflictResolutionOperations) {
		ImRegion[] tables = getRowConnectedTables(table);
		ImRegion[] toSplitRowCells = getRegionsOverlapping(table.getPage(), toSplitRow.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(toSplitRowCells, leftRightOrder);
		WordBlock[] toSplitRowWords = getWordBlocksForRowSplit(table.getPage(), toSplitRowCells, toSplitRow.bounds, splitBox);
		
		//	compute relative split bounds
		int relAboveSplitBottom = 0;
		int relSplitTop = (splitBox.bottom - toSplitRow.bounds.top);
		int relSplitBottom = (splitBox.top - toSplitRow.bounds.top);
		int relBelowSplitTop = (toSplitRow.bounds.bottom - toSplitRow.bounds.top);
		
		//	order words above, inside, and below selection
		for (int w = 0; w < toSplitRowWords.length; w++) {
			if (toSplitRowWords[w].centerY < splitBox.top)
				relAboveSplitBottom = Math.max(relAboveSplitBottom, (toSplitRowWords[w].bounds.bottom - toSplitRow.bounds.top));
			else if (toSplitRowWords[w].centerY < splitBox.bottom) {
				relSplitTop = Math.min(relSplitTop, (toSplitRowWords[w].bounds.top - toSplitRow.bounds.top));
				relSplitBottom = Math.max(relSplitBottom, (toSplitRowWords[w].bounds.bottom - toSplitRow.bounds.top));
			}
			else relBelowSplitTop = Math.min(relBelowSplitTop, (toSplitRowWords[w].bounds.top - toSplitRow.bounds.top));
		}
		
		//	anything to split at all?
		int emptySplitRows = 0;
		if (relAboveSplitBottom == 0)
			emptySplitRows++;
		if (relSplitBottom <= relSplitTop)
			emptySplitRows++;
		if (relBelowSplitTop == (toSplitRow.bounds.bottom - toSplitRow.bounds.top))
			emptySplitRows++;
		if (emptySplitRows >= 2)
			return false;
		
		//	check if we have words obstructing the split
		HashSet multiRowWords = null;
		if ((relSplitTop < relAboveSplitBottom) || (relBelowSplitTop < relSplitBottom)) {
			
			//	determine bottom edge of all words _completely_ above of selection ...
			//	... and top edge of all words _completely_ below of selection ...
			int aboveSplitBottom = toSplitRow.bounds.top;
			int belowSplitTop = toSplitRow.bounds.bottom;
			ArrayList insideSplitWords = new ArrayList();
			for (int w = 0; w < toSplitRowWords.length; w++) {
				if (toSplitRowWords[w].bounds.bottom <= splitBox.top)
					aboveSplitBottom = Math.max(aboveSplitBottom, toSplitRowWords[w].bounds.bottom);
				else if (splitBox.bottom <= toSplitRowWords[w].bounds.top)
					belowSplitTop = Math.min(belowSplitTop, toSplitRowWords[w].bounds.top);
				else insideSplitWords.add(toSplitRowWords[w]);
			}
			
			//	mark all remaining words that protrude beyond (or even only close to ???) either of these edges as multi-row
			//	... and determine boundaries of middle row from the rest
			//	TODO depending on average row gap, be even more sensitive about multi-row cells ...
			//	... as they might end up failing to completely reach edge of neighbor row and only get close
			int splitTop = splitBox.bottom;
			int splitBottom = splitBox.top;
			multiRowWords = new HashSet();
			for ( int w = 0; w < insideSplitWords.size(); w++) {
				WordBlock isWord = ((WordBlock) insideSplitWords.get(w));
				if ((toSplitRow.bounds.top < aboveSplitBottom) && (isWord.bounds.top <= aboveSplitBottom))
					multiRowWords.add(isWord);
				else if ((belowSplitTop < toSplitRow.bounds.bottom) && (belowSplitTop <= isWord.bounds.bottom))
					multiRowWords.add(isWord);
				else {
					splitTop = Math.min(splitTop, isWord.bounds.top);
					splitBottom = Math.max(splitBottom, isWord.bounds.bottom);
				}
			}
			
			//	re-compute relative split bounds
			relAboveSplitBottom = (aboveSplitBottom - toSplitRow.bounds.top);
			relSplitTop = (splitTop - toSplitRow.bounds.top);
			relSplitBottom = (splitBottom - toSplitRow.bounds.top);
			relBelowSplitTop = (belowSplitTop - toSplitRow.bounds.top);
		}
		
		//	cut effort short in basic case
		if (tables.length == 1) {
			doSplitTableRow(table.getPage(), table, toSplitRow, toSplitRowWords, multiRowWords, relAboveSplitBottom, relSplitTop, relSplitBottom, relBelowSplitTop, conflictResolutionOperations);
			return true;
		}
		
		//	get connected table rows
		ImRegion[] toSplitTableRows = getConnectedTableRows(table, tables, toSplitRow);
		if (toSplitTableRows == null)
			return false;
		
		//	perform split
		float relTopMiddleSplit = (((float) (relAboveSplitBottom + relSplitTop)) / (2 * (toSplitRow.bounds.bottom - toSplitRow.bounds.top)));
		float relMiddleBottomSplit = (((float) (relSplitBottom + relBelowSplitTop)) / (2 * (toSplitRow.bounds.bottom - toSplitRow.bounds.top)));
		for (int t = 0; t < tables.length; t++) {
			if (tables[t] == table)
				doSplitTableRow(tables[t].getPage(), tables[t], toSplitTableRows[t], toSplitRowWords, multiRowWords, relAboveSplitBottom, relSplitTop, relSplitBottom, relBelowSplitTop, conflictResolutionOperations);
			else doSplitTableRow(tables[t], toSplitTableRows[t], relTopMiddleSplit, relMiddleBottomSplit, conflictResolutionOperations);
		}
		return true;
	}
	
	private static void doSplitTableRow(ImRegion table, ImRegion toSplitRow, float relTopMiddleSplit, float relMiddleBottomSplit, Properties conflictResolutionOperations) {
		
		//	compute relative split box
		int splitBoxTop = (((int) (relTopMiddleSplit * (toSplitRow.bounds.bottom - toSplitRow.bounds.top))) + toSplitRow.bounds.top);
		int splitBoxBottom = (((int) (relMiddleBottomSplit * (toSplitRow.bounds.bottom - toSplitRow.bounds.top))) + toSplitRow.bounds.top);
		
		//	compute relative split bounds
		int relAboveSplitBottom = 0;
		int relSplitTop = (splitBoxBottom - toSplitRow.bounds.top);
		int relSplitBottom = (splitBoxTop - toSplitRow.bounds.top);
		int relBelowSplitTop = (toSplitRow.bounds.bottom - toSplitRow.bounds.top);
		
		//	order words above, inside, and below selection
		ImRegion[] toSplitRowCells = getRegionsOverlapping(table.getPage(), toSplitRow.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(toSplitRowCells, leftRightOrder);
		WordBlock[] toSplitRowWords = getWordBlocksForRowSplit(table.getPage(), toSplitRowCells, toSplitRow.bounds, new BoundingBox(table.bounds.left, table.bounds.right, splitBoxTop, splitBoxBottom));
		for (int w = 0; w < toSplitRowWords.length; w++) {
			if (toSplitRowWords[w].centerY < splitBoxTop)
				relAboveSplitBottom = Math.max(relAboveSplitBottom, (toSplitRowWords[w].bounds.bottom - toSplitRow.bounds.top));
			else if (toSplitRowWords[w].centerY < splitBoxBottom) {
				relSplitTop = Math.min(relSplitTop, (toSplitRowWords[w].bounds.top - toSplitRow.bounds.top));
				relSplitBottom = Math.max(relSplitBottom, (toSplitRowWords[w].bounds.bottom - toSplitRow.bounds.top));
			}
			else relBelowSplitTop = Math.min(relBelowSplitTop, (toSplitRowWords[w].bounds.top - toSplitRow.bounds.top));
		}
		
		//	check if we have words obstructing the split
		HashSet multiRowWords = null;
		if ((relSplitTop < relAboveSplitBottom) || (relBelowSplitTop < relSplitBottom)) {
			
			//	determine bottom edge of all words _completely_ above of selection ...
			//	... and top edge of all words _completely_ below of selection ...
			int aboveSplitBottom = toSplitRow.bounds.top;
			int belowSplitTop = toSplitRow.bounds.bottom;
			ArrayList insideSplitWords = new ArrayList();
			for (int w = 0; w < toSplitRowWords.length; w++) {
				if (toSplitRowWords[w].bounds.bottom <= splitBoxTop)
					aboveSplitBottom = Math.max(aboveSplitBottom, toSplitRowWords[w].bounds.bottom);
				else if (splitBoxBottom <= toSplitRowWords[w].bounds.top)
					belowSplitTop = Math.min(belowSplitTop, toSplitRowWords[w].bounds.top);
				else insideSplitWords.add(toSplitRowWords[w]);
			}
			
			//	mark all remaining words that protrude beyond (or even only close to ???) either of these edges as multi-row
			//	... and determine boundaries of middle row from the rest
			//	TODO depending on average row gap, be even more sensitive about multi-row cells ...
			//	... as they might end up failing to completely reach edge of neighbor row and only get close
			int splitTop = splitBoxBottom;
			int splitBottom = splitBoxTop;
			multiRowWords = new HashSet();
			for ( int w = 0; w < insideSplitWords.size(); w++) {
				WordBlock isWord = ((WordBlock) insideSplitWords.get(w));
				if ((toSplitRow.bounds.top < aboveSplitBottom) && (isWord.bounds.top <= aboveSplitBottom))
					multiRowWords.add(isWord);
				else if ((belowSplitTop < toSplitRow.bounds.bottom) && (belowSplitTop <= isWord.bounds.bottom))
					multiRowWords.add(isWord);
				else {
					splitTop = Math.min(splitTop, isWord.bounds.top);
					splitBottom = Math.max(splitBottom, isWord.bounds.bottom);
				}
			}
			
			//	re-compute relative split bounds
			relAboveSplitBottom = (aboveSplitBottom - toSplitRow.bounds.top);
			relSplitTop = (splitTop - toSplitRow.bounds.top);
			relSplitBottom = (splitBottom - toSplitRow.bounds.top);
			relBelowSplitTop = (belowSplitTop - toSplitRow.bounds.top);
		}
		
		//	do split with case adjusted absolute numbers
		doSplitTableRow(table.getPage(), table, toSplitRow, toSplitRowWords, multiRowWords, relAboveSplitBottom, relSplitTop, relSplitBottom, relBelowSplitTop, conflictResolutionOperations);
	}
	
	private static void doSplitTableRow(ImPage page, ImRegion table, ImRegion toSplitRow, WordBlock[] toSplitRowWords, HashSet multiRowWords, int relAboveSplitBottom, int relSplitTop, int relSplitBottom, int relBelowSplitTop, Properties conflictResolutionOperations) {
		
		//	create two or three new rows
		if (0 < relAboveSplitBottom) {
			BoundingBox arBox = new BoundingBox(table.bounds.left, table.bounds.right, toSplitRow.bounds.top, (toSplitRow.bounds.top + relAboveSplitBottom));
			ImRegion sRow = new ImRegion(page, arBox, ImRegion.TABLE_ROW_TYPE);
			if (toSplitRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		if (relSplitTop < relSplitBottom) {
			BoundingBox irBox = new BoundingBox(table.bounds.left, table.bounds.right, (toSplitRow.bounds.top + relSplitTop), (toSplitRow.bounds.top + relSplitBottom));
			ImRegion sRow = new ImRegion(page, irBox, ImRegion.TABLE_ROW_TYPE);
			if (toSplitRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		if (relBelowSplitTop < (toSplitRow.bounds.bottom - toSplitRow.bounds.top)) {
			BoundingBox brBox = new BoundingBox(table.bounds.left, table.bounds.right, (toSplitRow.bounds.top + relBelowSplitTop), toSplitRow.bounds.bottom);
			ImRegion sRow = new ImRegion(page, brBox, ImRegion.TABLE_ROW_TYPE);
			if (toSplitRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				sRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
		}
		
		//	remove selected row
		page.removeRegion(toSplitRow);
		
		//	get split rows and existing cells
		ImRegion[] sRows = page.getRegionsInside(ImRegion.TABLE_ROW_TYPE, toSplitRow.bounds, false);
		Arrays.sort(sRows, topDownOrder);
		ImRegion[] toSplitRowCells = getRegionsOverlapping(page, toSplitRow.bounds, ImRegion.TABLE_CELL_TYPE);
		Arrays.sort(toSplitRowCells, leftRightOrder);
		
		//	create new cells left to right
		for (int l = 0; l < toSplitRowCells.length; l++) {
			
			//	handle base case, saving all the hassle below
			if (multiRowWords == null) {
				
				//	create split cells
				for (int r = 0; r < sRows.length; r++) {
					int sCellTop = ((r == 0) ? toSplitRowCells[l].bounds.top : sRows[r].bounds.top);
					int sCellBottom = (((r+1) == sRows.length) ? toSplitRowCells[l].bounds.bottom : sRows[r].bounds.bottom);
					markTableCell(page, new BoundingBox(toSplitRowCells[l].bounds.left, toSplitRowCells[l].bounds.right, sCellTop, sCellBottom), true, false);
				}
				
				//	clean up and we're done here
				page.removeRegion(toSplitRowCells[l]);
				continue;
			}
			
			//	check for multi-row cells
			boolean[] cellReachesIntoNextRow = new boolean[sRows.length - 1];
			Arrays.fill(cellReachesIntoNextRow, false);
			for (int r = 0; r < (sRows.length - 1); r++) {
				WordBlock[] sCellWords = getWordBlocksOverlapping(toSplitRowWords, new BoundingBox(toSplitRowCells[l].bounds.left, toSplitRowCells[l].bounds.right, sRows[r].bounds.top, sRows[r+1].bounds.bottom));
				if (sCellWords.length == 0)
					continue;
				Arrays.sort(sCellWords, topDownOrder);
				for (int w = 0; w < sCellWords.length; w++) {
					if (sRows[r+1].bounds.top < sCellWords[w].bounds.top)
						break; // we're only interested in words overlapping with space between the current pair of rows
					if (multiRowWords.contains(sCellWords[w])) {
						cellReachesIntoNextRow[r] = true;
						break; // one word overlapping both rows is enough
					}
				}
			}
			
			//	create cells for split rows
			int sCellTop = Math.min(toSplitRowCells[l].bounds.top, sRows[0].bounds.top);
			for (int r = 0; r < sRows.length; r++) {
				if ((r < cellReachesIntoNextRow.length) && cellReachesIntoNextRow[r])
					continue;
				if ((r+1) == sRows.length)
					markTableCell(page, new BoundingBox(toSplitRowCells[l].bounds.left, toSplitRowCells[l].bounds.right, sCellTop, toSplitRowCells[l].bounds.bottom), true, false);
				else {
					markTableCell(page, new BoundingBox(toSplitRowCells[l].bounds.left, toSplitRowCells[l].bounds.right, sCellTop, sRows[r].bounds.bottom), true, false);
					sCellTop = sRows[r+1].bounds.top;
				}
			}
			
			//	clean up
			page.removeRegion(toSplitRowCells[l]);
		}
		
		//	clean up table structure
		orderTableWords(markTableCells(page, table));
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
	}
//	
//	private static class TableRowLine {
//		final ImWord[] words;
//		final ImRegion table;
//		final HashSet overlappingLines = new HashSet();
//		final HashSet overlappingWords = new HashSet();
//		int top;
//		int bottom;
//		TableRowLine(ImWord[] words, ImRegion table) {
//			this.words = words;
//			int top = Integer.MAX_VALUE;
//			int bottom = Integer.MIN_VALUE;
//			for (int w = 0; w < this.words.length; w++) {
//				top = Math.min(top, this.words[w].bounds.top);
//				bottom = Math.max(bottom, this.words[w].bounds.bottom);
//			}
//			this.top = top;
//			this.bottom = bottom;
//			this.table = table;
//			System.out.println("   - " + this.getBounds());
//		}
//		BoundingBox getBounds() {
//			return new BoundingBox(this.table.bounds.left, this.table.bounds.right, this.top, this.bottom);
//		}
//	}
//	
//	/**
//	 * Split a table row at al all viable horizontal gaps. This method does not
//	 * affect the rows in any tables whose rows are connected to the argument
//	 * table. In case the rows are connected, <code>splitTableRow()</code>
//	 * should be used instead.
//	 * @param table the table the row belongs to
//	 * @param toSplitRow the table row to split
//	 * @param splitBox the box defining where to split
//	 * @param conflictResolutionOperations a map indicating how to resolve any
//	 *            cell boundary conflicts for individual annotation types
//	 * @return true if the table was modified
//	 */
//	public static boolean splitTableRowToLines(ImPage page, ImRegion table, ImRegion toSplitRow, Properties conflictResolutionOperations) {
////		System.out.println("Splitting " + toSplitRow.bounds + " to lines");
//		
//		//	get table row words
//		ImWord[] toSplitRowWords = toSplitRow.getWords();
//		sortLeftRightTopDown(toSplitRowWords);
////		System.out.println(" - words sorted");
//		
//		//	sort words into rows
//		int rowStartWordIndex = 0;
//		ArrayList rowLines = new ArrayList();
////		System.out.println(" - collecting line rows");
//		for (int w = 0; w < toSplitRowWords.length; w++)
//			if (((w+1) == toSplitRowWords.length) || (toSplitRowWords[w+1].centerY > toSplitRowWords[w].bounds.bottom)) {
//				rowLines.add(new TableRowLine(Arrays.copyOfRange(toSplitRowWords, rowStartWordIndex, (w+1)), table));
//				rowStartWordIndex = (w+1);
//			}
////		System.out.println(" - line rows collected");
//		
//		//	check for conflicts
//		System.out.println(" - checking for conflicts");
//		for (int l = 0; l < rowLines.size(); l++) {
//			TableRowLine rowLine = ((TableRowLine) rowLines.get(l));
//			for (int cl = 0; cl < rowLines.size(); cl++) {
//				if (cl == l)
//					continue;
//				TableRowLine cRowLine = ((TableRowLine) rowLines.get(cl));
//				if (cRowLine.bottom <= rowLine.top)
//					continue;
//				if (rowLine.bottom <= cRowLine.top)
//					break;
//				rowLine.overlappingLines.add(cRowLine);
//				cRowLine.overlappingLines.add(rowLine);
//			}
////			System.out.println("   - found " + rowLine.overlappingLines.size() + " conflicts for " + rowLine.getBounds());
//		}
//		
//		//	resolve conflicts, and collect between-line words
//		HashSet betweenLineWords = new HashSet();
//		System.out.println(" - resolving conflicts");
//		for (boolean lineOverlapResolved = true; lineOverlapResolved;) {
//			lineOverlapResolved = false;
//			
//			//	find line overlapping maximum number of other lines
//			int maxOverlappingLines = 0;
//			TableRowLine maxOverlappedLine = null;
//			ArrayList maxOverlappedLines = new ArrayList();
//			for (int l = 0; l < rowLines.size(); l++) {
//				TableRowLine rowLine = ((TableRowLine) rowLines.get(l));
//				if (maxOverlappingLines < rowLine.overlappingLines.size()) {
//					maxOverlappingLines = rowLine.overlappingLines.size();
//					maxOverlappedLine = rowLine;
//					maxOverlappedLines.clear();
//					maxOverlappedLines.add(rowLine);
//				}
//				else if (maxOverlappingLines == 0) {}
//				else if (maxOverlappingLines == rowLine.overlappingLines.size())
//					maxOverlappedLines.add(rowLine);
//			}
//			if (maxOverlappedLine == null)
//				break;
//			
//			//	if we have multiple lines with same number of conflicting rows, first take care of the one with most non-conflicting words
//			if (maxOverlappedLines.size() > 1) {
////				System.out.println(" - choosing between " + maxOverlappedLines.size() + " lines overlapping with " + maxOverlappingLines + " others");
//				int maxNonOverlappingWordCount = 0;
//				for (int l = 0; l < maxOverlappedLines.size(); l++) {
//					TableRowLine rowLine = ((TableRowLine) maxOverlappedLines.get(l));
//					HashSet nonOverlappingWords = new HashSet(Arrays.asList(rowLine.words));
//					for (Iterator olit = rowLine.overlappingLines.iterator(); olit.hasNext();) {
//						TableRowLine oRowLine = ((TableRowLine) olit.next());
//						for (int w = 0; w < rowLine.words.length; w++) {
//							if (rowLine.words[w].bounds.bottom <= oRowLine.top) { /* word above conflicting line */ }
//							else if (oRowLine.bottom <= rowLine.words[w].bounds.top) { /* word below conflicting line */ }
//							else nonOverlappingWords.remove(rowLine.words[w]);
//						}
//					}
////					System.out.println("   - " + rowLine.getBounds() + " has " + nonOverlappingWords.size() + " non-conflicting words");
//					if (maxNonOverlappingWordCount < nonOverlappingWords.size()) {
//						maxNonOverlappingWordCount = nonOverlappingWords.size();
//						maxOverlappedLine = rowLine;
//					}
//				}
////				System.out.println("   ==> selected " + maxOverlappedLine.getBounds() + " with " + maxNonOverlappingWordCount + " non-conflicting words");
//			}
//			lineOverlapResolved = true; // we're going to resolve this one ... somehow ...
////			System.out.println("   - shrinking " + maxOverlappedLine.getBounds() + " with " + maxOverlappedLine.overlappingLines.size() + " overlapping lines");
//			
//			//	collect words in said line that don't overlap other lines
//			HashSet nonOverlappingWords = new HashSet(Arrays.asList(maxOverlappedLine.words));
//			for (Iterator olit = maxOverlappedLine.overlappingLines.iterator(); olit.hasNext();) {
//				TableRowLine oRowLine = ((TableRowLine) olit.next());
//				for (int w = 0; w < maxOverlappedLine.words.length; w++) {
//					if (maxOverlappedLine.words[w].bounds.bottom <= oRowLine.top) { /* word above conflicting line */ }
//					else if (oRowLine.bottom <= maxOverlappedLine.words[w].bounds.top) { /* word below conflicting line */ }
//					else nonOverlappingWords.remove(maxOverlappedLine.words[w]);
//				}
//			}
//			
//			//	no words remaining, eliminate it and collect words for latter cell mergers
//			if (nonOverlappingWords.isEmpty()) {
//				betweenLineWords.addAll(Arrays.asList(maxOverlappedLine.words));
//				rowLines.remove(maxOverlappedLine);
//				for (int l = 0; l < rowLines.size(); l++)
//					((TableRowLine) rowLines.get(l)).overlappingLines.remove(maxOverlappedLine);
////				System.out.println("     ==> eliminated altogether (1)");
//				continue;
//			}
//			
//			//	shrink line to non-conflicting dimensions
//			int nonOverlappingTop = Integer.MAX_VALUE;
//			int nonOverlappingBottom = Integer.MIN_VALUE;
//			for (Iterator nowit = nonOverlappingWords.iterator(); nowit.hasNext();) {
//				ImWord noWord = ((ImWord) nowit.next());
//				nonOverlappingTop = Math.min(nonOverlappingTop, noWord.bounds.top);
//				nonOverlappingBottom = Math.max(nonOverlappingBottom, noWord.bounds.bottom);
//			}
//			if (nonOverlappingBottom <= nonOverlappingTop) {
//				betweenLineWords.addAll(Arrays.asList(maxOverlappedLine.words));
//				rowLines.remove(maxOverlappedLine);
//				System.out.println("     ==> eliminated altogether (2)");
//			}
//			else {
//				maxOverlappedLine.top = nonOverlappingTop;
//				maxOverlappedLine.bottom = nonOverlappingBottom;
//				for (int w = 0; w < maxOverlappedLine.words.length; w++) {
//					if (!nonOverlappingWords.contains(maxOverlappedLine.words[w]))
//						maxOverlappedLine.overlappingWords.add(maxOverlappedLine.words[w]);
//				}
////				System.out.println("     ==> shrunk to " + maxOverlappedLine.getBounds());
////				System.out.println("     ==> got conflict inducing words " + maxOverlappedLine.overlappingWords);
//			}
//			for (int l = 0; l < rowLines.size(); l++)
//				((TableRowLine) rowLines.get(l)).overlappingLines.remove(maxOverlappedLine);
//			maxOverlappedLine.overlappingLines.clear();
//		}
//		
//		//	mark line rows
//		ImRegion[] sRows = new ImRegion[rowLines.size()];
//		for (int l = 0; l < rowLines.size(); l++)
//			sRows[l] = new ImRegion(table.getPage(), ((TableRowLine) rowLines.get(l)).getBounds(), ImRegion.TABLE_ROW_TYPE);
////		System.out.println(" - line rows added");
//		
//		//	remove selected row
//		table.getPage().removeRegion(toSplitRow);
////		System.out.println(" - to split row removed");
//		
//		//	get split rows and existing cells
////		System.out.println(" - got " + sRows.length + " line rows");
//		ImRegion[] toSplitRowCells = getRegionsOverlapping(page, toSplitRow.bounds, ImRegion.TABLE_CELL_TYPE);
//		Arrays.sort(toSplitRowCells, leftRightOrder);
////		System.out.println(" - got " + toSplitRowCells.length + " cells to split");
//		
//		//	create new cells left to right
//		for (int c = 0; c < toSplitRowCells.length; c++) {
////			System.out.println(" - splitting cell " + toSplitRowCells[c].bounds);
//			
//			//	create split cells
//			for (int r = 0; r < sRows.length; r++) {
//				int sCellTop = ((r == 0) ? toSplitRowCells[c].bounds.top : sRows[r].bounds.top);
//				int sCellBottom = (((r+1) == sRows.length) ? toSplitRowCells[c].bounds.bottom : sRows[r].bounds.bottom);
//				ImRegion sCell = markTableCell(page, new BoundingBox(toSplitRowCells[c].bounds.left, toSplitRowCells[c].bounds.right, sCellTop, sCellBottom), true, false);
////				System.out.println("   - " + sCell.bounds);
//			}
//			
//			//	clean up
//			page.removeRegion(toSplitRowCells[c]);
////			System.out.println("   - cell removed");
//		}
//		
//		//	merge cells with overlapping content
//		for (int l = 0; l < rowLines.size(); l++)
//			betweenLineWords.addAll(((TableRowLine) rowLines.get(l)).overlappingWords);
//		for (Iterator blwit = betweenLineWords.iterator(); blwit.hasNext();) {
//			ImWord blWord = ((ImWord) blwit.next());
////			System.out.println(" - merging cells overlapping " + blWord);
//			ImRegion[] blWordCells = getRegionsOverlapping(page, blWord.bounds, ImRegion.TABLE_CELL_TYPE);
////			System.out.println("   - got " + blWordCells.length + " overlapping cells");
//			if (blWordCells.length < 2)
//				continue;
//			BoundingBox mCellBounds = ImLayoutObject.getAggregateBox(blWordCells);
//			ImRegion mCell = markTableCell(page, mCellBounds, true, false);
////			System.out.println("   - got merged cell " + mCell.bounds);
//			for (int c = 0; c < blWordCells.length; c++)
//				page.removeRegion(blWordCells[c]);
////			System.out.println("   - merged cells removed");
//		}
//		
//		//	clean up table structure
//		orderTableWords(markTableCells(page, table));
//		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
//		
//		//	finally ...
//		return true;
//	}
	
	private static WordBlock[] getWordBlocksForRowSplit(ImPage page, ImRegion[] cells, BoundingBox bounds, BoundingBox splitBounds) {
		
		//	compute average row margin within and across split
		int inCellRowGapSum = 0;
		int inCellRowGapCount = 0;
		int crossCellRowGapSum = 0;
		int crossCellRowGapCount = 0;
		int wordHeightSum = 0;
		int wordHeightCount = 0;
		for (int c = 0; c < cells.length; c++) {
			ImWord[] cellWords = page.getWordsInside(cells[c].bounds);
			sortLeftRightTopDown(cellWords);
			for (int w = 0; w < cellWords.length; w++) {
				wordHeightSum += cellWords[w].bounds.getHeight();
				wordHeightCount++;
				if ((w + 1) == cellWords.length)
					break; // only need to count height from last word
				if (cellWords[w].bounds.bottom > cellWords[w+1].centerY)
					continue; // same line, no gap to analyze here
				if (cellWords[w].bounds.right <= cellWords[w+1].bounds.left)
					continue; // horizontal offset to right, unsafe
				if (cellWords[w].centerY < splitBounds.top) {
					if (cellWords[w+1].centerY < splitBounds.top) {
						inCellRowGapSum += (cellWords[w+1].bounds.top - cellWords[w].bounds.bottom);
						inCellRowGapCount++;
					}
					else {
						crossCellRowGapSum += (cellWords[w+1].bounds.top - cellWords[w].bounds.bottom);
						crossCellRowGapCount++;
					}
				}
				else if (cellWords[w].centerY < splitBounds.bottom) {
					if (cellWords[w+1].centerY < splitBounds.bottom) {
						inCellRowGapSum += (cellWords[w+1].bounds.top - cellWords[w].bounds.bottom);
						inCellRowGapCount++;
					}
					else {
						crossCellRowGapSum += (cellWords[w+1].bounds.top - cellWords[w].bounds.bottom);
						crossCellRowGapCount++;
					}
				}
				else {
					inCellRowGapSum += (cellWords[w+1].bounds.top - cellWords[w].bounds.bottom);
					inCellRowGapCount++;
				}
			}
		}
		int inCellRowGap = ((inCellRowGapCount == 0) ? -1 : (inCellRowGapSum / inCellRowGapCount));
		int crossCellRowGap = ((crossCellRowGapCount == 0) ? -1 : (crossCellRowGapSum / crossCellRowGapCount));
		int wordHeight = ((wordHeightCount == 0) ? 0 : (wordHeightSum / wordHeightCount));
//		System.out.println("In-cell row gap is " + inCellRowGap + ", cross-cell row gap is " + crossCellRowGap + ", word height is " + wordHeight);
		
		//	fall back to word height based estimates if gaps are 0
		if (inCellRowGap == -1)
			inCellRowGap = (wordHeight / 4);
		if (crossCellRowGap == -1)
			crossCellRowGap = (wordHeight / 2);
		
		//	aggregate words to blocks for individual cells
		ArrayList wbs = new ArrayList();
		for (int c = 0; c < cells.length; c++) {
			ImWord[] cellWords = page.getWordsInside(cells[c].bounds);
			sortLeftRightTopDown(cellWords);
			int wbStart = 0;
			for (int w = 1; w <= cellWords.length; w++) {
//				if ((w == cellWords.length) || this.isRowGap(cellWords[w-1], cellWords[w], inCellRowGap, crossCellRowGap)) {
				if ((w == cellWords.length) || isRowGap(cellWords[w-1], cellWords[w], inCellRowGap, crossCellRowGap)) {
					ImWord[] wbWords = new ImWord[w - wbStart];
					System.arraycopy(cellWords, wbStart, wbWords, 0, wbWords.length);
					WordBlock wb = new WordBlock(page, wbWords, false);
//					System.out.println("Word block: " + wb + " at " + wb.bounds);
					wbs.add(wb);
					wbStart = w;
				}
			}
		}
		
		//	finally ...
		return ((WordBlock[]) wbs.toArray(new WordBlock[wbs.size()]));
	}
	
	private static boolean isRowGap(ImWord word1, ImWord word2, int inCellRowGap, int crossCellRowGap) {
		if (word1.bounds.bottom > word2.centerY)
			return false; // presumably same line
		if (word1.bounds.bottom > word2.bounds.top)
			return false; // no gap at all
		if (word1.getNextRelation() == ImWord.NEXT_RELATION_CONTINUE)
			return false; // explicit continuation marker
		if (word1.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED)
			return false; // explicit continuation marker
		if ((inCellRowGap * 2) > crossCellRowGap)
			return true; // too close to call, stay safe and avoid gluing words together
		int wordGap = (word2.bounds.top - word1.bounds.bottom);
		return ((crossCellRowGap - wordGap) < (wordGap - inCellRowGap)); // TODO make sure to measure sensibly
	}
	
	private static ImRegion[] getRegionsOverlapping(ImPage page, BoundingBox box, String type) {
		ImRegion[] regions = page.getRegions(type);
		ArrayList regionList = new ArrayList();
		for (int r = 0; r < regions.length; r++) {
			if (regions[r].bounds.overlaps(box))
				regionList.add(regions[r]);
		}
		return ((ImRegion[]) regionList.toArray(new ImRegion[regionList.size()]));
	}
	
	private static ImWord[] getWordsOverlapping(ImWord[] words, BoundingBox box) {
		ArrayList wordList = new ArrayList();
		for (int w = 0; w < words.length; w++) {
			if (words[w].bounds.overlaps(box))
				wordList.add(words[w]);
		}
		return ((ImWord[]) wordList.toArray(new ImWord[wordList.size()]));
	}
	
	private static WordBlock[] getWordBlocksOverlapping(WordBlock[] wordBlocks, BoundingBox box) {
		ArrayList wbs = new ArrayList();
		for (int b = 0; b < wordBlocks.length; b++) {
			if (wordBlocks[b].bounds.overlaps(box))
				wbs.add(wordBlocks[b]);
		}
		return ((WordBlock[]) wbs.toArray(new WordBlock[wbs.size()]));
	}
	
	private static class WordBlock extends ImRegion {
		final int centerX;
		final int centerY;
		final ImWord[] words;
		final boolean forColSplit;
		WordBlock(ImPage page, ImWord[] words, boolean forColSplit) {
			super(page.getDocument(), page.pageId, ImLayoutObject.getAggregateBox(words), "wordBlock");
			this.centerX = ((this.bounds.left + this.bounds.right) / 2);
			this.centerY = ((this.bounds.top + this.bounds.bottom) / 2);
			this.words = words;
			this.forColSplit = forColSplit;
		}
		public String toString() {
			return getString(this.words[0], this.words[this.words.length-1], this.forColSplit);
		}
	}
	
	/**
	 * Merge up a set of adjacent table cells.
	 * @param table the table the cells belong to
	 * @param mergeCells the cells to merge
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean mergeTableCells(ImRegion table, ImRegion[] mergeCells, Properties conflictResolutionOperations) {
		ImPage page = table.getPage();
		
		//	get aggregate cell bounds (transitive hull, to keep cells rectangular)
		BoundingBox mCellBounds = ImLayoutObject.getAggregateBox(mergeCells);
		while (true) {
			mergeCells = getRegionsOverlapping(page, mCellBounds, ImRegion.TABLE_CELL_TYPE);
			BoundingBox eMergedBounds = ImLayoutObject.getAggregateBox(mergeCells);
			if (mCellBounds.equals(eMergedBounds))
				break;
			else mCellBounds = eMergedBounds;
		}
		
		//	clean up factually merged cells (not only originally selected ones, but all in transitive hull of merge result)
		mergeCells = page.getRegionsInside(ImRegion.TABLE_CELL_TYPE, mCellBounds, false);
		for (int c = 0; c < mergeCells.length; c++)
			page.removeRegion(mergeCells[c]);
		
		//	mark aggregate cell
		ImRegion mCell = new ImRegion(page, mCellBounds, ImRegion.TABLE_CELL_TYPE);
		
		//	shrink affected rows and remaining fully contained cells
		ImRegion[] mCellRows = getRegionsOverlapping(page, mCell.bounds, ImRegion.TABLE_ROW_TYPE);
		Arrays.sort(mCellRows, topDownOrder);
		if (mCellRows.length > 1)
			for (int r = 0; r < mCellRows.length; r++) {
				
				//	assess words contained in cells that do not reach beyond row
				ImRegion[] mCellRowCells = getRegionsOverlapping(page, mCellRows[r].bounds, ImRegion.TABLE_CELL_TYPE);
				int mCellRowTop = mCellRows[r].bounds.bottom;
				int mCellRowBottom = mCellRows[r].bounds.top;
				for (int l = 0; l < mCellRowCells.length; l++) {
					if (mCell.bounds.overlaps(mCellRowCells[l].bounds))
						continue; // no use considering merge result here
					ImWord[] mCellRowCellWords = page.getWordsInside(mCellRowCells[l].bounds);
					for (int w = 0; w < mCellRowCellWords.length; w++) {
						if (mCellRows[r].bounds.top <= mCellRowCells[l].bounds.top)
							mCellRowTop = Math.min(mCellRowTop, mCellRowCellWords[w].bounds.top);
						if (mCellRowCells[l].bounds.bottom <= mCellRows[r].bounds.bottom)
							mCellRowBottom = Math.max(mCellRowBottom, mCellRowCellWords[w].bounds.bottom);
					}
				}
				if ((mCellRowTop <= mCellRows[r].bounds.top) && (mCellRows[r].bounds.bottom <= mCellRowBottom))
					continue; // nothing to shrink here
				
				//	don't shrink outside edges of edge rows
				if (r == 0)
					mCellRowTop = Math.min(mCellRowTop, mCellRows[r].bounds.top);
				if (r == (mCellRows.length - 1))
					mCellRowBottom = Math.max(mCellRowBottom, mCellRows[r].bounds.bottom);
				
				//	compute reduced bounds
				BoundingBox mCellRowBox = new BoundingBox(table.bounds.left, table.bounds.right, mCellRowTop, mCellRowBottom);
				
				//	shrink cells to reduced row height
				for (int l = 0; l < mCellRowCells.length; l++) {
					if (mCell.bounds.overlaps(mCellRowCells[l].bounds))
						continue; // don't shrink merge result
					page.removeRegion(mCellRowCells[l]);
					BoundingBox mCellRowCellBox = new BoundingBox(
							mCellRowCells[l].bounds.left,
							mCellRowCells[l].bounds.right,
							((mCellRows[r].bounds.top <= mCellRowCells[l].bounds.top) ? mCellRowTop : mCellRowCells[l].bounds.top),
							((mCellRowCells[l].bounds.bottom <= mCellRows[r].bounds.bottom) ? mCellRowBottom : mCellRowCells[l].bounds.bottom)
						);
					mCellRowCells[l] = new ImRegion(page, mCellRowCellBox, ImRegion.TABLE_CELL_TYPE);
 				}
				
				//	shrink row proper
				boolean isHeaderRow = mCellRows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(mCellRows[r]);
				mCellRows[r] = new ImRegion(page, mCellRowBox, ImRegion.TABLE_ROW_TYPE);
				if (isHeaderRow)
					mCellRows[r].setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
			}
		
		//	shrink affected columns to remaining fully contained cells
		ImRegion[] mCellCols = getRegionsOverlapping(page, mCell.bounds, ImRegion.TABLE_COL_TYPE);
		Arrays.sort(mCellCols, leftRightOrder);
		if (mCellCols.length > 1)
			for (int c = 0; c < mCellCols.length; c++) {
				
				//	assess words contained in cells that do not reach beyond column
				ImRegion[] mCellColCells = getRegionsOverlapping(page, mCellCols[c].bounds, ImRegion.TABLE_CELL_TYPE);
				int mCellColLeft = mCellCols[c].bounds.right;
				int mCellColRight = mCellCols[c].bounds.left;
				for (int l = 0; l < mCellColCells.length; l++) {
					if (mCell.bounds.overlaps(mCellColCells[l].bounds))
						continue; // no use considering merge result here
					ImWord[] mCellRowCellWords = page.getWordsInside(mCellColCells[l].bounds);
					for (int w = 0; w < mCellRowCellWords.length; w++) {
						if (mCellCols[c].bounds.left <= mCellColCells[l].bounds.left)
							mCellColLeft = Math.min(mCellColLeft, mCellRowCellWords[w].bounds.left);
						if (mCellColCells[l].bounds.right <= mCellCols[c].bounds.right)
							mCellColRight = Math.max(mCellColRight, mCellRowCellWords[w].bounds.right);
					}
				}
				
				//	don't shrink outside edges of edge columns
				if (c == 0)
					mCellColLeft = Math.min(mCellColLeft, mCellCols[c].bounds.left);
				if (c == (mCellCols.length - 1))
					mCellColRight = Math.max(mCellColRight, mCellCols[c].bounds.right);
				
				//	anything to shrink at all?
				if ((mCellColLeft <= mCellCols[c].bounds.left) && (mCellCols[c].bounds.right <= mCellColRight))
					continue;
				
				//	compute reduced bounds
				BoundingBox mCellColBox = new BoundingBox(mCellColLeft, mCellColRight, table.bounds.top, table.bounds.bottom);
				
				//	shrink cells to reduced row height
				for (int l = 0; l < mCellColCells.length; l++) {
					if (mCell.bounds.overlaps(mCellColCells[l].bounds))
						continue; // don't shrink merge result
					page.removeRegion(mCellColCells[l]);
					BoundingBox mCellColCellBox = new BoundingBox(
							((mCellCols[c].bounds.left <= mCellColCells[l].bounds.left) ? mCellColLeft : mCellColCells[l].bounds.left),
							((mCellColCells[l].bounds.right <= mCellCols[c].bounds.right) ? mCellColRight : mCellColCells[l].bounds.right),
							mCellColCells[l].bounds.top,
							mCellColCells[l].bounds.bottom
						);
					mCellColCells[l] = new ImRegion(page, mCellColCellBox, ImRegion.TABLE_CELL_TYPE);
 				}
				
				//	shrink column proper
				boolean isLabelCol = mCellCols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(mCellCols[c]);
				mCellCols[c] = new ImRegion(page, mCellColBox, ImRegion.TABLE_COL_TYPE);
				if (isLabelCol)
					mCellCols[c].setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
			}
		
		//	update table
		orderTableWords(markTableCells(page, table));
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
		
		//	indicate change
		return true;
	}
	
	private static final boolean DEBUG_SPLIT_CELLS = false;
	
	/**
	 * Split up a table cell along column boundaries. Depending on the
	 * splitting box, the argument table cell is split into two (splitting off
	 * left or right) or three (splitting across the middle) new cells.
	 * @param table the table the cell belongs to
	 * @param toSplitCell the table cell to split
	 * @param toSplitCellColumns the table columns overlapping the cell to split
	 * @param splitBox the bounding box indicating where to split
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean splitTableCellColumns(ImRegion table, ImRegion toSplitCell, ImRegion[] toSplitCellColumns, BoundingBox splitBox, Properties conflictResolutionOperations) {
		Arrays.sort(toSplitCellColumns, leftRightOrder);
		ImPage page = table.getPage();
		
		//	order words above, inside, and below selection
		ArrayList leftSplitCols = new ArrayList();
		ArrayList splitCols = new ArrayList();
		ArrayList rightSplitCols = new ArrayList();
		for (int c = 0; c < toSplitCellColumns.length; c++) {
			int colCenterX = ((toSplitCellColumns[c].bounds.left + toSplitCellColumns[c].bounds.right) / 2);
			if (colCenterX < splitBox.left)
				leftSplitCols.add(toSplitCellColumns[c]);
			else if (colCenterX < splitBox.right)
				splitCols.add(toSplitCellColumns[c]);
			else rightSplitCols.add(toSplitCellColumns[c]);
		}
		if (DEBUG_SPLIT_CELLS) {
			System.out.println("Splitting table cell at " + toSplitCell.pageId + "." + toSplitCell.bounds + " to columns");
			System.out.println(" - got " + leftSplitCols.size() + " columns to left of split, " + splitCols.size() + " within, and " + rightSplitCols.size() + " to right");
		}
		
		//	anything to split at all?
		int emptySplitCols = 0;
		if (leftSplitCols.isEmpty())
			emptySplitCols++;
		if (splitCols.isEmpty())
			emptySplitCols++;
		if (rightSplitCols.isEmpty())
			emptySplitCols++;
		if (emptySplitCols >= 2)
			return false;
		
		//	remove selected cell
		page.removeRegion(toSplitCell);
		
		//	sort words in to-split cell
		ImWord[] toSplitCellWords = page.getWordsInside(toSplitCell.bounds);
		
		//	perform split
		if (leftSplitCols.size() != 0) {
			ImRegion leftSplitRightCol = ((ImRegion) leftSplitCols.get(leftSplitCols.size() - 1));
			int leftSplitCellLeft = ((ImRegion) leftSplitCols.get(0)).bounds.left;
			int leftSplitCellRight = leftSplitRightCol.bounds.right;
			for (int w = 0; w < toSplitCellWords.length; w++) {
				if (toSplitCellWords[w].centerX < splitBox.left)
					leftSplitCellRight = Math.max(leftSplitCellRight, toSplitCellWords[w].bounds.right);
			}
			
			BoundingBox lscBox = new BoundingBox(leftSplitCellLeft, leftSplitCellRight, toSplitCell.bounds.top, toSplitCell.bounds.bottom);
			markTableCell(page, lscBox, (leftSplitCols.size() == 1), false);
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell left of split at " + lscBox);
			
			if (leftSplitRightCol.bounds.right < leftSplitCellRight) {
				boolean isLabelCol = leftSplitRightCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(leftSplitRightCol);
				leftSplitRightCol = new ImRegion(page, new BoundingBox(leftSplitRightCol.bounds.left, leftSplitCellRight, leftSplitRightCol.bounds.top, leftSplitRightCol.bounds.bottom), ImRegion.TABLE_COL_TYPE);
				if (isLabelCol)
					leftSplitRightCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				
				ImRegion[] leftSplitRightCells = getRegionsOverlapping(page, leftSplitRightCol.bounds, ImRegion.TABLE_CELL_TYPE);
				for (int c = 0; c < leftSplitRightCells.length; c++) {
					if (leftSplitCellRight <= leftSplitRightCells[c].bounds.right)
						continue; // multi-column cell that reaches rightward of split, no need for expanding
					page.removeRegion(leftSplitRightCells[c]);
					lscBox = new BoundingBox(leftSplitRightCells[c].bounds.left, leftSplitCellRight, leftSplitRightCells[c].bounds.top, leftSplitRightCells[c].bounds.bottom);
					markTableCell(page, lscBox, (leftSplitCols.size() == 1), false);
				}
			}
		}
		
		if (splitCols.size() != 0) {
			ImRegion splitLeftCol = ((ImRegion) splitCols.get(0));
			ImRegion splitRightCol = ((ImRegion) splitCols.get(splitCols.size() - 1));
			int splitCellLeft = splitLeftCol.bounds.left;
			int splitCellRight = splitRightCol.bounds.right;
			for (int w = 0; w < toSplitCellWords.length; w++)
				if ((splitBox.left <= toSplitCellWords[w].centerX) && (toSplitCellWords[w].centerX < splitBox.right)) {
					splitCellLeft = Math.min(splitCellLeft, toSplitCellWords[w].bounds.left);
					splitCellRight = Math.max(splitCellRight, toSplitCellWords[w].bounds.right);
				}
			
			BoundingBox iscBox = new BoundingBox(splitCellLeft, splitCellRight, toSplitCell.bounds.top, toSplitCell.bounds.bottom);
			markTableCell(page, iscBox, (splitCols.size() == 1), false);
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell within split at " + iscBox);
			
			if (splitLeftCol == splitRightCol) {
				if ((splitCellLeft < splitLeftCol.bounds.left) || (splitLeftCol.bounds.right < splitCellRight)) {
					boolean isLabelCol = splitLeftCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitLeftCol);
					splitLeftCol = new ImRegion(page, new BoundingBox(Math.min(splitCellLeft, splitLeftCol.bounds.left), Math.max(splitCellRight, splitLeftCol.bounds.right), splitLeftCol.bounds.top, splitLeftCol.bounds.bottom), ImRegion.TABLE_COL_TYPE);
					if (isLabelCol)
						splitLeftCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitCells = getRegionsOverlapping(page, splitLeftCol.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitCells.length; c++) {
						if ((splitCells[c].bounds.left <= splitCellLeft) && (splitCellRight <= splitCells[c].bounds.right))
							continue; // multi-row cell that reaches leftward and rightward of split, no need for expanding
						page.removeRegion(splitCells[c]);
						iscBox = new BoundingBox(Math.min(splitCellLeft, splitCells[c].bounds.left), Math.max(splitCellRight, splitCells[c].bounds.right), splitCells[c].bounds.top, splitCells[c].bounds.bottom);
						markTableCell(page, iscBox, (rightSplitCols.size() == 1), false);
					}
				}
			}
			else {
				if (splitCellLeft < splitLeftCol.bounds.left) {
					boolean isLabelCol = splitLeftCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitLeftCol);
					splitLeftCol = new ImRegion(page, new BoundingBox(splitCellLeft, splitLeftCol.bounds.right, splitLeftCol.bounds.top, splitLeftCol.bounds.bottom), ImRegion.TABLE_COL_TYPE);
					if (isLabelCol)
						splitLeftCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitLeftCells = getRegionsOverlapping(page, splitLeftCol.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitLeftCells.length; c++) {
						if (splitLeftCells[c].bounds.left <= splitCellLeft)
							continue; // multi-column cell that reaches leftward of split, no need for expanding
						page.removeRegion(splitLeftCells[c]);
						iscBox = new BoundingBox(splitCellLeft, splitLeftCells[c].bounds.right, splitLeftCells[c].bounds.top, splitLeftCells[c].bounds.bottom);
						markTableCell(page, iscBox, (rightSplitCols.size() == 1), false);
					}
				}
				if (splitRightCol.bounds.right < splitCellRight) {
					boolean isLabelCol = splitRightCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitRightCol);
					splitRightCol = new ImRegion(page, new BoundingBox(splitRightCol.bounds.left, splitCellRight, splitRightCol.bounds.top, splitRightCol.bounds.bottom), ImRegion.TABLE_COL_TYPE);
					if (isLabelCol)
						splitRightCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitRightCells = getRegionsOverlapping(page, splitRightCol.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitRightCells.length; c++) {
						if (splitCellRight <= splitRightCells[c].bounds.right)
							continue; // multi-column cell that reaches rightward of split, no need for expanding
						page.removeRegion(splitRightCells[c]);
						iscBox = new BoundingBox(splitRightCells[c].bounds.left, splitCellRight, splitRightCells[c].bounds.top, splitRightCells[c].bounds.bottom);
						markTableCell(page, iscBox, (leftSplitCols.size() == 1), false);
					}
				}
			}
		}
		
		if (rightSplitCols.size() != 0) {
			ImRegion rightSplitLeftCol = ((ImRegion) rightSplitCols.get(0));
			int rightSplitCellLeft = rightSplitLeftCol.bounds.left;
			int rightSplitCellRight = ((ImRegion) rightSplitCols.get(rightSplitCols.size() - 1)).bounds.right;
			for (int w = 0; w < toSplitCellWords.length; w++) {
				if (splitBox.right <= toSplitCellWords[w].centerX)
					rightSplitCellLeft = Math.min(rightSplitCellLeft, toSplitCellWords[w].bounds.left);
			}
			
			BoundingBox rscBox = new BoundingBox(rightSplitCellLeft, rightSplitCellRight, toSplitCell.bounds.top, toSplitCell.bounds.bottom);
			markTableCell(page, rscBox, (rightSplitCols.size() == 1), false);
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell right of split at " + rscBox);
			
			if (rightSplitCellLeft < rightSplitLeftCol.bounds.left) {
				boolean isLabelCol = rightSplitLeftCol.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(rightSplitLeftCol);
				rightSplitLeftCol = new ImRegion(page, new BoundingBox(rightSplitCellLeft, rightSplitLeftCol.bounds.right, rightSplitLeftCol.bounds.top, rightSplitLeftCol.bounds.bottom), ImRegion.TABLE_COL_TYPE);
				if (isLabelCol)
					rightSplitLeftCol.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				
				ImRegion[] rightSplitLeftCells = getRegionsOverlapping(page, rightSplitLeftCol.bounds, ImRegion.TABLE_CELL_TYPE);
				for (int c = 0; c < rightSplitLeftCells.length; c++) {
					if (rightSplitLeftCells[c].bounds.left <= rightSplitCellLeft)
						continue; // multi-column cell that reaches leftward of split, no need for expanding
					page.removeRegion(rightSplitLeftCells[c]);
					rscBox = new BoundingBox(rightSplitCellLeft, rightSplitLeftCells[c].bounds.right, rightSplitLeftCells[c].bounds.top, rightSplitLeftCells[c].bounds.bottom);
					markTableCell(page, rscBox, (rightSplitCols.size() == 1), false);
				}
			}
		}
		
		//	clean up table structure
		orderTableWords(markTableCells(page, table));
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
		if (DEBUG_SPLIT_CELLS) System.out.println(" - table words and annotations cleaned up");
		
		//	indicate we changed something
		return true;
	}
	
	/**
	 * Split up a table cell along row boundaries. Depending on the splitting
	 * box, the argument table cell is split into two (splitting off top or
	 * bottom) or three (splitting across the middle) new cells.
	 * @param table the table the cell belongs to
	 * @param toSplitCell the table cell to split
	 * @param toSplitCellRows the table rows overlapping the cell to split
	 * @param splitBox the bounding box indicating where to split
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if the table was modified
	 */
	public static boolean splitTableCellRows(ImRegion table, ImRegion toSplitCell, ImRegion[] toSplitCellRows, BoundingBox splitBox, Properties conflictResolutionOperations) {
		Arrays.sort(toSplitCellRows, topDownOrder);
		ImPage page = table.getPage();
		
		//	order words above, inside, and below selection
		ArrayList aboveSplitRows = new ArrayList();
		ArrayList splitRows = new ArrayList();
		ArrayList belowSplitRows = new ArrayList();
		for (int r = 0; r < toSplitCellRows.length; r++) {
			int rowCenterY = ((toSplitCellRows[r].bounds.top + toSplitCellRows[r].bounds.bottom) / 2);
			if (rowCenterY < splitBox.top)
				aboveSplitRows.add(toSplitCellRows[r]);
			else if (rowCenterY < splitBox.bottom)
				splitRows.add(toSplitCellRows[r]);
			else belowSplitRows.add(toSplitCellRows[r]);
		}
		if (DEBUG_SPLIT_CELLS) {
			System.out.println("Splitting table cell at " + toSplitCell.pageId + "." + toSplitCell.bounds + " to rows");
			System.out.println(" - got " + aboveSplitRows.size() + " rows above split, " + splitRows.size() + " within, and " + belowSplitRows.size() + " below");
		}
		
		//	anything to split at all?
		int emptySplitRows = 0;
		if (aboveSplitRows.isEmpty())
			emptySplitRows++;
		if (splitRows.isEmpty())
			emptySplitRows++;
		if (belowSplitRows.isEmpty())
			emptySplitRows++;
		if (emptySplitRows >= 2)
			return false;
		
		//	remove selected cell
		page.removeRegion(toSplitCell);
		
		//	sort words in to-split cell
		ImWord[] toSplitCellWords = page.getWordsInside(toSplitCell.bounds);
		
		//	perform split
		if (aboveSplitRows.size() != 0) {
			ImRegion aboveSplitBottomRow = ((ImRegion) aboveSplitRows.get(aboveSplitRows.size() - 1));
			int aboveSplitCellTop = ((ImRegion) aboveSplitRows.get(0)).bounds.top;
			int aboveSplitCellBottom = aboveSplitBottomRow.bounds.bottom;
			for (int w = 0; w < toSplitCellWords.length; w++) {
				if (toSplitCellWords[w].centerY < splitBox.top)
					aboveSplitCellBottom = Math.max(aboveSplitCellBottom, toSplitCellWords[w].bounds.bottom);
			}
			
			BoundingBox ascBox = new BoundingBox(toSplitCell.bounds.left, toSplitCell.bounds.right, aboveSplitCellTop, aboveSplitCellBottom);
			markTableCell(page, ascBox, true, (aboveSplitRows.size() == 1));
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell above split at " + ascBox);
			
			if (aboveSplitBottomRow.bounds.bottom < aboveSplitCellBottom) {
				boolean isHeaderRow = aboveSplitBottomRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(aboveSplitBottomRow);
				aboveSplitBottomRow = new ImRegion(page, new BoundingBox(aboveSplitBottomRow.bounds.left, aboveSplitBottomRow.bounds.right, aboveSplitBottomRow.bounds.top, aboveSplitCellBottom), ImRegion.TABLE_ROW_TYPE);
				if (isHeaderRow)
					aboveSplitBottomRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				
				ImRegion[] aboveSplitBottomCells = getRegionsOverlapping(page, aboveSplitBottomRow.bounds, ImRegion.TABLE_CELL_TYPE);
				for (int c = 0; c < aboveSplitBottomCells.length; c++) {
					if (aboveSplitCellBottom <= aboveSplitBottomCells[c].bounds.bottom)
						continue; // multi-row cell that reaches below split, no need for expanding
					page.removeRegion(aboveSplitBottomCells[c]);
					ascBox = new BoundingBox(aboveSplitBottomCells[c].bounds.left, aboveSplitBottomCells[c].bounds.right, aboveSplitBottomCells[c].bounds.top, aboveSplitCellBottom);
					markTableCell(page, ascBox, true, (aboveSplitRows.size() == 1));
				}
			}
		}
		
		if (splitRows.size() != 0) {
			ImRegion splitTopRow = ((ImRegion) splitRows.get(0));
			ImRegion splitBottomRow = ((ImRegion) splitRows.get(splitRows.size() - 1));
			int splitCellTop = splitTopRow.bounds.top;
			int splitCellBottom = splitBottomRow.bounds.bottom;
			for (int w = 0; w < toSplitCellWords.length; w++)
				if ((splitBox.top <= toSplitCellWords[w].centerY) && (toSplitCellWords[w].centerY < splitBox.bottom)) {
					splitCellTop = Math.min(splitCellTop, toSplitCellWords[w].bounds.top);
					splitCellBottom = Math.max(splitCellBottom, toSplitCellWords[w].bounds.bottom);
				}
			
			BoundingBox iscBox = new BoundingBox(toSplitCell.bounds.left, toSplitCell.bounds.right, splitCellTop, splitCellBottom);
			markTableCell(page, iscBox, true, (splitRows.size() == 1));
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell withing split at " + iscBox);
			
			if (splitTopRow == splitBottomRow) {
				if ((splitCellTop < splitTopRow.bounds.top) || (splitTopRow.bounds.bottom < splitCellBottom)) {
					boolean isHeaderRow = splitTopRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitTopRow);
					splitTopRow = new ImRegion(page, new BoundingBox(splitTopRow.bounds.left, splitTopRow.bounds.right, Math.min(splitCellTop, splitTopRow.bounds.top), Math.max(splitCellBottom, splitTopRow.bounds.bottom)), ImRegion.TABLE_ROW_TYPE);
					if (isHeaderRow)
						splitTopRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitCells = getRegionsOverlapping(page, splitTopRow.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitCells.length; c++) {
						if ((splitCells[c].bounds.top <= splitCellTop) && (splitCellBottom <= splitCells[c].bounds.bottom))
							continue; // multi-row cell that reaches above and below split, no need for expanding
						page.removeRegion(splitCells[c]);
						iscBox = new BoundingBox(splitCells[c].bounds.left, splitCells[c].bounds.right, Math.min(splitCellTop, splitCells[c].bounds.top), Math.max(splitCellBottom, splitCells[c].bounds.bottom));
						markTableCell(page, iscBox, true, (splitRows.size() == 1));
					}
				}
			}
			else {
				if (splitCellTop < splitTopRow.bounds.top) {
					boolean isHeaderRow = splitTopRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitTopRow);
					splitTopRow = new ImRegion(page, new BoundingBox(splitTopRow.bounds.left, splitTopRow.bounds.right, splitCellTop, splitTopRow.bounds.bottom), ImRegion.TABLE_ROW_TYPE);
					if (isHeaderRow)
						splitTopRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitTopCells = getRegionsOverlapping(page, splitTopRow.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitTopCells.length; c++) {
						if (splitTopCells[c].bounds.top <= splitCellTop)
							continue; // multi-row cell that reaches above split, no need for expanding
						page.removeRegion(splitTopCells[c]);
						iscBox = new BoundingBox(splitTopCells[c].bounds.left, splitTopCells[c].bounds.right, splitCellTop, splitTopCells[c].bounds.bottom);
						markTableCell(page, iscBox, true, (belowSplitRows.size() == 1));
					}
				}
				if (splitBottomRow.bounds.bottom < splitCellBottom) {
					boolean isHeaderRow = splitBottomRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					page.removeRegion(splitBottomRow);
					splitBottomRow = new ImRegion(page, new BoundingBox(splitBottomRow.bounds.left, splitBottomRow.bounds.right, splitBottomRow.bounds.top, splitCellBottom), ImRegion.TABLE_ROW_TYPE);
					if (isHeaderRow)
						splitBottomRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
					
					ImRegion[] splitBottomCells = getRegionsOverlapping(page, splitBottomRow.bounds, ImRegion.TABLE_CELL_TYPE);
					for (int c = 0; c < splitBottomCells.length; c++) {
						if (splitCellBottom <= splitBottomCells[c].bounds.bottom)
							continue; // multi-row cell that reaches below split, no need for expanding
						page.removeRegion(splitBottomCells[c]);
						iscBox = new BoundingBox(splitBottomCells[c].bounds.left, splitBottomCells[c].bounds.right, splitBottomCells[c].bounds.top, splitCellBottom);
						markTableCell(page, iscBox, true, (aboveSplitRows.size() == 1));
					}
				}
			}
		}
		
		if (belowSplitRows.size() != 0) {
			ImRegion belowSplitTopRow = ((ImRegion) belowSplitRows.get(0));
			int belowSplitCellTop = belowSplitTopRow.bounds.top;
			int belowSplitCellBottom = ((ImRegion) belowSplitRows.get(belowSplitRows.size() - 1)).bounds.bottom;
			for (int w = 0; w < toSplitCellWords.length; w++) {
				if (splitBox.bottom <= toSplitCellWords[w].centerY)
					belowSplitCellTop = Math.min(belowSplitCellTop, toSplitCellWords[w].bounds.top);
			}
			
			BoundingBox bscBox = new BoundingBox(toSplitCell.bounds.left, toSplitCell.bounds.right, belowSplitCellTop, belowSplitCellBottom);
			markTableCell(page, bscBox, true, (belowSplitRows.size() == 1));
			if (DEBUG_SPLIT_CELLS) System.out.println(" - marked table cell below split at " + bscBox);
			
			if (belowSplitCellTop < belowSplitTopRow.bounds.top) {
				boolean isHeaderRow = belowSplitTopRow.hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				page.removeRegion(belowSplitTopRow);
				belowSplitTopRow = new ImRegion(page, new BoundingBox(belowSplitTopRow.bounds.left, belowSplitTopRow.bounds.right, belowSplitCellTop, belowSplitTopRow.bounds.bottom), ImRegion.TABLE_ROW_TYPE);
				if (isHeaderRow)
					belowSplitTopRow.setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
				
				ImRegion[] belowSplitTopCells = getRegionsOverlapping(page, belowSplitTopRow.bounds, ImRegion.TABLE_CELL_TYPE);
				for (int c = 0; c < belowSplitTopCells.length; c++) {
					if (belowSplitTopCells[c].bounds.top <= belowSplitCellTop)
						continue; // multi-row cell that reaches above split, no need for expanding
					page.removeRegion(belowSplitTopCells[c]);
					bscBox = new BoundingBox(belowSplitTopCells[c].bounds.left, belowSplitTopCells[c].bounds.right, belowSplitCellTop, belowSplitTopCells[c].bounds.bottom);
					markTableCell(page, bscBox, true, (belowSplitRows.size() == 1));
				}
			}
		}
		
		//	clean up table structure
		orderTableWords(markTableCells(page, table));
		cleanupTableAnnotations(table.getDocument(), table, conflictResolutionOperations);
		if (DEBUG_SPLIT_CELLS) System.out.println(" - table words and annotations cleaned up");
		
		//	indicate we changed something
		return true;
	}
	
	//	TODO_not remove this method, does nothing but call some constructor
	private static ImRegion markTableCell(ImPage page, BoundingBox bounds, boolean shrinkLeftRight, boolean shrinkTopBottom) {
//		//	TODOne DO NOT SHRINK !!!
//		//	TODOne TEST WITHOUT SHRINKING !!! ==> TODOne only need to shrink _columns_ or _rows_ before marking cells
//		if (shrinkLeftRight || shrinkTopBottom) {
//			ImWord[] tableCellWords = page.getWordsInside(bounds);
//			if (tableCellWords.length != 0) {
//				BoundingBox wordBounds = ImLayoutObject.getAggregateBox(tableCellWords);
//				bounds = new BoundingBox(
//					(shrinkLeftRight ? Math.max(bounds.left, wordBounds.left) : bounds.left),
//					(shrinkLeftRight ? Math.min(bounds.right, wordBounds.right) : bounds.right),
//					(shrinkTopBottom ? Math.max(bounds.top, wordBounds.top) : bounds.top),
//					(shrinkTopBottom ? Math.min(bounds.bottom, wordBounds.bottom) : bounds.bottom)
//				);
//			}
//		}
		return new ImRegion(page, bounds, ImRegion.TABLE_CELL_TYPE);
	}
	
	private static final boolean DEBUG_SYNCHRONIZE_TABLE_HEADERS = false;
	
	/**
	 * Synchronize the disposition of potentially complex column header rows
	 * in a series of column-connected tables. In particular, this pertains to
	 * replicating connections of cells across columns and rows, splitting and
	 * merging header cells in connected tables as needed. If selected bounding
	 * boxes are specified and none of them overlaps with the table header
	 * area, this method does nothing.
	 * @param table the table whose header layout to replicate in column
	 *            connected tables
	 * @param selBoxes the bounding boxes enclosing the modification to
	 *            replicate
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if any of the connected tables was modified
	 */
	public static boolean synchronizeConnectedTableHeaders(ImRegion table, BoundingBox[] selBoxes, Properties conflictResolutionOperations) {
		if (!table.hasAttribute("colsContinueIn") && !table.hasAttribute("colsContinueFrom"))
			return false; // no need to even collect header rows without connected tables to synchronize
		ImRegion[] tableRows = table.getRegions(ImRegion.TABLE_ROW_TYPE, true);
		Arrays.sort(tableRows, ImUtils.topDownOrder);
		ArrayList headerRows = new ArrayList();
		boolean headerRowSelected = false;
		for (int r = 0; r < tableRows.length; r++) {
			if (!tableRows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				continue;
			headerRows.add(tableRows[r]);
			if (headerRowSelected || (selBoxes == null))
				headerRowSelected = true;
			else for (int b = 0; b < selBoxes.length; b++) {
				if (selBoxes[b].overlaps(tableRows[r].bounds))
					headerRowSelected = true;
			}
		}
		if (!headerRowSelected)
			return false; // header not affected
		if (headerRows.isEmpty())
			return false; // nothing to work with
		return synchronizeConnectedTableHeaders(table, ((ImRegion[]) headerRows.toArray(new ImRegion[headerRows.size()])), conflictResolutionOperations);
	}
	
	/**
	 * Synchronize the disposition of potentially complex column header rows
	 * in a series of column-connected tables. In particular, this pertains to
	 * replicating connections of cells across columns and rows, splitting and
	 * merging header cells in connected tables as needed. If selected bounding
	 * boxes are specified and none of them overlaps with the table header
	 * area, this method does nothing.
	 * @param table the table whose header layout to replicate in column
	 *            connected tables
	 * @param headerRows the header rows whose cell disposition to replicate
	 * @param conflictResolutionOperations a map indicating how to resolve any
	 *            cell boundary conflicts for individual annotation types
	 * @return true if any of the connected tables was modified
	 */
	public static boolean synchronizeConnectedTableHeaders(ImRegion table, ImRegion[] headerRows, Properties conflictResolutionOperations) {
		if (!table.hasAttribute("colsContinueIn") && !table.hasAttribute("colsContinueFrom"))
			return false; // no connected tables to synchronize
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("Synchronizing table headers");
		
		//	wrap header section
		ImTableHeader modelIth = ImTableHeader.wrapTableHeader(table, headerRows);
		if (modelIth == null)
			return false; // not convex, no dice on this one
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println(" - source table header wrapped, bounds are " + modelIth.aBounds + "/" + modelIth.rBounds + ", with " + modelIth.rows.length + " rows");
		
		//	get column connected tables and propagate changes
		ImRegion[] conTables = ImUtils.getColumnConnectedTables(table);
		if (conTables.length < 2)
			return false;
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println(" - got " + conTables.length + " column connected tables");
		
		//	try and propagate adjustments
		boolean modified = false;
		for (int t = 0; t < conTables.length; t++) {
			if (conTables[t] == table)
				continue; // this is the one we started from
			if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println(" - handling table at " + conTables[t].pageId + "." + conTables[t].bounds);
			
			//	wrap header and ensure basic match with model
			ImTableHeader conIth = ImTableHeader.wrapTableHeader(conTables[t], modelIth);
			if (conIth == null) {
				if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("   ==> could not wrap table header");
				continue;
			}
			if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("   - table header wrapped, bounds are " + conIth.aBounds + "/" + conIth.rBounds + ", with " + conIth.rows.length + " rows");
			if (!modelIth.matches(conIth)) {
				if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("     ==> mis-match");
				continue;
			}
			if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("   - matched to model, adjusting");
			
			//	perform synchronization
			if (synchronizeTabkeHeaders(modelIth, conIth, conflictResolutionOperations))
				modified = true;
		}
		
		//	did we change anything at all?
		return modified;
	}
	
	private static boolean synchronizeTabkeHeaders(ImTableHeader modelIth, ImTableHeader conIth, Properties conflictResolutionOperations) {
		boolean modified = false;
		
		//	perform required cell splits
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("     - splitting mingled cells");
		boolean cellSplit;
		do {
			cellSplit = false;
			
			//	split between columns first, row by row
			for (int r = 0; r < modelIth.rows.length; r++)
				for (int c = 1; c < modelIth.cols.length; c++) {
					if (modelIth.sectors[r][c-1].cell == modelIth.sectors[r][c].cell)
						continue; // cells connected in model header, we handle mergers below
					if (conIth.sectors[r][c-1].cell != conIth.sectors[r][c].cell)
						continue; // cells not connected in connected header, we're good
					if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("       - resolving leftward migle in sector column " + c + " row " + r);
					
					//	split in relative counterpart of middle of gap between sectors in model
					int rGapMiddle = (((modelIth.sectors[r][c-1].rBounds.right + modelIth.sectors[r][c].rBounds.left) / 2) - modelIth.sectors[r][c-1].rBounds.left + conIth.sectors[r][c-1].rBounds.left);
					BoundingBox rSplitBounds = new BoundingBox(conIth.sectors[r][c-1].rBounds.left, rGapMiddle, /* need to use absolute cell top and bottom, might be connected across rows as well */ conIth.sectors[r][c-1].cell.bounds.top, conIth.sectors[r][c-1].cell.bounds.bottom);
					BoundingBox aSplitBounds = rSplitBounds.translate(conIth.table.bounds.left, 0);
					ArrayList toSplitCellCols = new ArrayList();
					toSplitCellCols.add(conIth.sectors[r][c-1].col);
					toSplitCellCols.add(conIth.sectors[r][c].col);
					for (int lc = (c+1); lc < conIth.cols.length; lc++) {
						if (conIth.sectors[r][c].cell == conIth.sectors[r][lc].cell)
							toSplitCellCols.add(conIth.sectors[r][lc].col);
						else break;
					}
					if (ImUtils.splitTableCellColumns(conIth.table, conIth.sectors[r][c-1].cell, ((ImRegion[]) toSplitCellCols.toArray(new ImRegion[toSplitCellCols.size()])), aSplitBounds, conflictResolutionOperations)) {
						conIth = ImTableHeader.wrapTableHeader(conIth.table, modelIth);
						if (conIth == null) {
							if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> failed to re-wrap header");
							return true; // failed to re-wrap connected header ... indicate change, but abort synchronization
						}
						modified = true;
						cellSplit = true;
						if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> cell split");
					}
				}
			
			//	split between rows second
			for (int c = 0; c < modelIth.cols.length; c++)
				for (int r = 1; r < modelIth.rows.length; r++) {
					if (modelIth.sectors[r-1][c].cell == modelIth.sectors[r][c].cell)
						continue; // cells connected in model header, we handle mergers below
					if (conIth.sectors[r-1][c].cell != conIth.sectors[r][c].cell)
						continue; // cells not connected in connected header, we're good
					if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("       - resolving upward migle in sector column " + c + " row " + r);
					
					//	split in relative counterpart of middle of gap between sectors in model
					int rGapMiddle = (((modelIth.sectors[r-1][c].rBounds.bottom + modelIth.sectors[r][c].rBounds.top) / 2) - modelIth.sectors[r-1][c].rBounds.top + conIth.sectors[r-1][c].rBounds.top);
					BoundingBox rSplitBounds = new BoundingBox(/* need to use absolute cell left and right, might be connected across columns as well */ conIth.sectors[r-1][c].cell.bounds.left, conIth.sectors[r-1][c].cell.bounds.right, conIth.sectors[r-1][c].rBounds.top, rGapMiddle);
					BoundingBox aSplitBounds = rSplitBounds.translate(0, conIth.table.bounds.top);
					ArrayList toSplitCellRows = new ArrayList();
					toSplitCellRows.add(conIth.sectors[r-1][c].row);
					toSplitCellRows.add(conIth.sectors[r][c].row);
					for (int lr = (r+1); lr < conIth.rows.length; lr++) {
						if (conIth.sectors[r][c].cell == conIth.sectors[lr][c].cell)
							toSplitCellRows.add(conIth.sectors[lr][c].col);
						else break;
					}
					if (ImUtils.splitTableCellRows(conIth.table, conIth.sectors[r-1][c].cell, ((ImRegion[]) toSplitCellRows.toArray(new ImRegion[toSplitCellRows.size()])), aSplitBounds, conflictResolutionOperations)) {
						conIth = ImTableHeader.wrapTableHeader(conIth.table, modelIth);
						if (conIth == null) {
							if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> failed to re-wrap header");
							return true; // failed to re-wrap connected header ... indicate change, but abort synchronization
						}
						modified = true;
						cellSplit = true;
						if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> cell split");
					}
				}
		}
		while (cellSplit);
		
		//	merge cells across columns first (less invasive on text streams)
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("     - merging split cells across columns");
		for (int r = 0; r < modelIth.rows.length; r++)
			for (int c = 1; c < modelIth.cols.length; c++) {
				if (modelIth.sectors[r][c-1].cell != modelIth.sectors[r][c].cell)
					continue; // cells not connected in model header, nothing to do
				if (conIth.sectors[r][c-1].cell == conIth.sectors[r][c].cell)
					continue; // cells already connected in connected header as well, we're good
				if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("       - resolving split in sector column " + c + " row " + r);
				
				//	merge cells
				ImRegion[] mergeCells = {conIth.sectors[r][c-1].cell, conIth.sectors[r][c].cell};
				if (ImUtils.mergeTableCells(conIth.table, mergeCells, conflictResolutionOperations)) {
					conIth = ImTableHeader.wrapTableHeader(conIth.table, modelIth);
					if (conIth == null) {
						if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> failed to re-wrap header");
						return true; // failed to re-wrap connected header ... indicate change, but abort synchronization
					}
					modified = true;
					if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> cells merged");
				}
			}
		
		//	merge cells across rows second
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("     - merging split cells across rows");
		for (int c = 0; c < modelIth.cols.length; c++)
			for (int r = 1; r < modelIth.rows.length; r++) {
				if (modelIth.sectors[r-1][c].cell != modelIth.sectors[r][c].cell)
					continue; // cells not connected in model header, nothing to do
				if (conIth.sectors[r-1][c].cell == conIth.sectors[r][c].cell)
					continue; // cells already connected in connected header, we're good
				if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("       - resolving split in sector column " + c + " row " + r);
				
				//	merge cells
				ImRegion[] mergeCells = {conIth.sectors[r-1][c].cell, conIth.sectors[r][c].cell};
				if (ImUtils.mergeTableCells(conIth.table, mergeCells, conflictResolutionOperations)) {
					conIth = ImTableHeader.wrapTableHeader(conIth.table, modelIth);
					if (conIth == null) {
						if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> failed to re-wrap header");
						return true; // failed to re-wrap connected header ... indicate change, but abort synchronization
					}
					modified = true;
					if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("         ==> cells merged");
				}
			}
		
		//	adjust header marker flags of table rows involved
		if (DEBUG_SYNCHRONIZE_TABLE_HEADERS) System.out.println("     - adjusting header marker flags");
		ImRegion[] tableRows = conIth.table.getRegions(ImRegion.TABLE_ROW_TYPE, true);
		Arrays.sort(tableRows, ImUtils.topDownOrder);
		for (int r = 0; r < tableRows.length; r++) {
			boolean inConnectedIth = conIth.aBounds.includes(tableRows[r].bounds, true);
			if (inConnectedIth == tableRows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
				continue;
			if (inConnectedIth)
				tableRows[r].setAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
			else tableRows[r].removeAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE);
			modified = true;
		}
		
		//	did we change anything?
		return modified;
	}
	
	private static class ImTableHeader {
		final ImRegion table;
		final ImRegion[] cols;
		final ImRegion[] rows;
		final BoundingBox aBounds; // absolute, i.e., relative to page
		final BoundingBox rBounds; // relative to table
		final ImRegion[] cells;
		final ImTableWord[] words;
		final ImTableSector[][] sectors;
		ImTableHeader(ImRegion table, ImRegion[] cols, ImRegion[] rows, ImRegion[] cells) {
			this.table = table;
			this.cols = cols;
			this.rows = rows;
			this.aBounds = new BoundingBox(this.table.bounds.left, this.table.bounds.right, this.table.bounds.top, this.rows[this.rows.length - 1].bounds.bottom);
			this.rBounds = this.aBounds.translate(-this.aBounds.left, -this.aBounds.top);
			this.cells = cells;
			ImWord[] words = this.table.getPage().getWordsInside(this.aBounds);
			this.words = new ImTableWord[words.length];
			for (int w = 0; w < words.length; w++)
				this.words[w] = new ImTableWord(this.table, words[w]);
			this.sectors = new ImTableSector[this.rows.length][this.cols.length];
			for (int r = 0; r < this.rows.length; r++) {
				for (int c = 0; c < this.cols.length; c++)
					this.sectors[r][c] = new ImTableSector(this.table, this.cols[c], this.rows[r], this.cells);
			}
		}
		
		boolean matches(ImTableHeader ith) {
			if (ith == null)
				return false;
			
			//	check if sectors align
			if (this.cols.length != ith.cols.length)
				return false;
			if (this.rows.length != ith.rows.length)
				return false;
			for (int r = 0; r < this.rows.length; r++)
				for (int c = 0; c < this.cols.length; c++) {
					if (!fuzzyMatch(this.sectors[r][c].rBounds, ith.sectors[r][c].rBounds))
						return false;
				}
			
			//	check if words align
			if (((this.words.length * 10) < (ith.words.length * 8)) || ((ith.words.length * 10) < (this.words.length * 8)))
				return false; // discrepancy of over 20% either way, just too much
			int tWordBoundsMatchCount = 0;
			HashSet ithBoundsMatchWords = new HashSet();
			int tWordMatchCount = 0;
			HashSet ithMatchWords = new HashSet();
			int matchCharCount = 0;
			int[] misMatchChars = new int[27];
			Arrays.fill(misMatchChars, 0);
			for (int tw = 0; tw < this.words.length; tw++) {
				boolean boundsMatch = false;
				String twStr = this.words[tw].word.getString();
				boolean match = false;
				for (int ow = 0; ow < ith.words.length; ow++) {
					if (!fuzzyMatch(this.words[tw].rBounds, ith.words[ow].rBounds))
						continue;
					boundsMatch = true;
					ithBoundsMatchWords.add(ith.words[ow]);
					String owStr = ith.words[ow].word.getString();
					if (twStr.equalsIgnoreCase(owStr)) {
						match = true;
						ithMatchWords.add(ith.words[ow]);
						break;
					}
				}
				if (boundsMatch)
					tWordBoundsMatchCount++;
				if (match) {
					tWordMatchCount++;
					matchCharCount += twStr.length();
				}
				else for (int c = 0; c < twStr.length(); c++) {
					char ch = Character.toLowerCase(StringUtils.getBaseChar(twStr.charAt(c)));
					if (('a' <= ch) && (ch < 'z'))
						misMatchChars[(int) (ch - 'a')]++;
					else misMatchChars[26]++;
				}
			}
			if ((tWordMatchCount == this.words.length) && (ithMatchWords.size() == ith.words.length))
				return true; // complete match, no need to check any further
			if (((tWordBoundsMatchCount * 10) < (this.words.length * 9)) || ((ithBoundsMatchWords.size() * 10) < (ith.words.length * 9)))
				return false; // below 90% overlap of word boundaries
			
			//	fuzzy match unmatched words
			for (int ow = 0; ow < ith.words.length; ow++) {
				if (ithMatchWords.contains(ith.words[ow]))
					continue;
				String owStr = ith.words[ow].word.getString();
				for (int c = 0; c < owStr.length(); c++) {
					char ch = Character.toLowerCase(StringUtils.getBaseChar(owStr.charAt(c)));
					if (('a' <= ch) && (ch < 'z'))
						misMatchChars[(int) (ch - 'a')]--;
					else misMatchChars[(int) 'z']--;
				}
			}
			int misMatchCharCount = 0;
			for (int c = 0; c < misMatchChars.length; c++)
				misMatchCharCount += Math.abs(misMatchChars[c]);
			
			//	we have a match is over 90% of characters match eventually
			return ((misMatchCharCount * 9 / 2 /* mismatches counted from both sides */) < matchCharCount);
		}
		
		static boolean fuzzyMatch(BoundingBox bb1, BoundingBox bb2) {
			if ((bb1 == null) || (bb2 == null))
				return false;
			if (bb1.includes(bb2, true) && bb2.includes(bb1, true))
				return true; // mutually contained
			if (bb1.includes(bb2, false))
				return true; // A fully contains B
			if (bb2.includes(bb1, false))
				return true; // B fully contains A
			if (bb1.includes(bb2, true)) {
				BoundingBox overlap = bb1.intersect(bb2);
				if ((bb2.getArea() * 9) < (overlap.getArea() * 10))
					return true; // A contains over 90% of B
			}
			if (bb2.includes(bb1, true)) {
				BoundingBox overlap = bb2.intersect(bb1);
				if ((bb1.getArea() * 9) < (overlap.getArea() * 10))
					return true; // B contains over 90% of A
			}
			return false;
		}
		
		static ImTableHeader wrapTableHeader(ImRegion table, ImRegion[] headerRows) {
			return wrapTableHeader(table, headerRows, true /* enforce unambiguous cell association for model header */);
		}
		static ImTableHeader wrapTableHeader(ImRegion table, ImTableHeader model) {
			
			//	translate model bounds to table at hand
			BoundingBox fuzzyHeaderBounds = new BoundingBox(table.bounds.left, table.bounds.right, table.bounds.top, (table.bounds.top + model.rBounds.getHeight()));
			
			//	extract header rows, and make sure none protrudes all too far
			ImRegion[] tableRows = table.getRegions(ImRegion.TABLE_ROW_TYPE, true);
			Arrays.sort(tableRows, ImUtils.topDownOrder);
			ArrayList headerRows = new ArrayList();
			for (int r = 0; r < tableRows.length; r++) {
				if (fuzzyHeaderBounds.includes(tableRows[r].bounds, true))
					headerRows.add(tableRows[r]);
				else if (fuzzyHeaderBounds.overlaps(tableRows[r].bounds))
					return null;
			}
			
			//	wrap header
			return wrapTableHeader(table, ((ImRegion[]) headerRows.toArray(new ImRegion[headerRows.size()])), false /* allow protruding cells here, we'll split them up on synchronizing */);
		}
		private static ImTableHeader wrapTableHeader(ImRegion table, ImRegion[] headerRows, boolean ensureConvex) {
			
			//	get cells and check if any protrude
			BoundingBox headerBounds = ImLayoutObject.getAggregateBox(headerRows);
			ImRegion[] tableCells = table.getRegions(ImRegion.TABLE_CELL_TYPE, false);
			ArrayList headerCells = new ArrayList();
			for (int c = 0; c < tableCells.length; c++) {
				if (headerBounds.includes(tableCells[c].bounds, false))
					headerCells.add(tableCells[c]);
				else if (headerBounds.overlaps(tableCells[c].bounds)) {
					if (ensureConvex)
						return null; // this one protrudes beyond header, not convex
					else headerCells.add(tableCells[c]); // need to include this one no matter haw large the overlap, as we might need to cut it
				}
			}
			
			//	get columns, sort, column and rows, and wrap header section
			Arrays.sort(headerRows, ImUtils.topDownOrder);
			ImRegion[] tableCols = table.getRegions(ImRegion.TABLE_COL_TYPE, false);
			Arrays.sort(tableCols, ImUtils.leftRightOrder);
			return new ImTableHeader(table, tableCols, headerRows, ((ImRegion[]) headerCells.toArray(new ImRegion[headerCells.size()])));
		}
	}
	
	private static class ImTableWord {
		final ImRegion table;
		final ImWord word;
		final BoundingBox rBounds; // relative to table
		ImTableWord(ImRegion table, ImWord word) {
			this.table = table;
			this.word = word;
			this.rBounds = this.word.bounds.translate(-this.table.bounds.left, -this.table.bounds.top);
		}
	}
	
	private static class ImTableSector {
		final ImRegion table;
		final ImRegion col;
		final ImRegion row;
		final BoundingBox aBounds; // absolute, i.e., relative to page
		final BoundingBox rBounds; // relative to table
		final ImRegion cell;
		ImTableSector(ImRegion table, ImRegion col, ImRegion row, ImRegion[] areaCells) {
			this.table = table;
			this.col = col;
			this.row = row;
			this.aBounds = new BoundingBox(this.col.bounds.left, this.col.bounds.right, this.row.bounds.top, this.row.bounds.bottom);
			this.rBounds = this.aBounds.translate(-this.table.bounds.left, -this.table.bounds.top);
			ImRegion cell = null;
			for (int c = 0; c < areaCells.length; c++)
				if(areaCells[c].bounds.includes(this.aBounds, true)) {
					cell = areaCells[c];
					break;
				}
			this.cell = cell;
		}
	}
	
	/**
	 * Copy the data from a table in some column based format like CSV. If the
	 * argument separator is the comma or semicolon, field values are enclosed
	 * in double quotes.
	 * @param table the table whose data to copy
	 * @param separator the separator to use
	 * @return the data from the argument table
	 */
	public static String getTableData(ImRegion table, char separator) {
		return getTableData(table, separator, false);
	}
	private static String getTableData(ImRegion table, char separator, boolean includeIDs) {
		return getTableOrTableGridDataString(table, false, separator, includeIDs);
	}
	
	/**
	 * Copy the data from a table in XML, specifically in XHTML.
	 * @param table the table whose data to copy
	 * @return the data from the argument table
	 */
	public static String getTableDataXml(ImRegion table) {
		return getTableDataXml(table, false, false);
	}
	
	/**
	 * Copy the data from a table in XML, specifically in XHTML. If
	 * <code>includeMarkup</code> argument is true, this method also outputs
	 * any annotation present inside table cells, which might result in output
	 * that does not conform to the definition of XHTML in a strict sense, as
	 * table cells an contain arbitrary XML elements.
	 * @param table the table whose data to copy
	 * @param includeMarkup add XML markup inside table cells?
	 * @return the data from the argument table
	 */
	public static String getTableDataXml(ImRegion table, boolean includeMarkup) {
		return getTableDataXml(table, includeMarkup, false);
	}
	
	/**
	 * Copy the data from a table in XML, specifically in XHTML. If
	 * <code>includeMarkup</code> argument is true, this method also outputs
	 * any annotation present inside table cells, which might result in output
	 * that does not conform to the definition of XHTML in a strict sense, as
	 * table cells an contain arbitrary XML elements.
	 * @param table the table whose data to copy
	 * @param includeMarkup add XML markup inside table cells?
	 * @param includeIDs include IDs of XML elements?
	 * @return the data from the argument table
	 */
	public static String getTableDataXml(ImRegion table, boolean includeMarkup, boolean includeIDs) {
		return getTableData(table, (includeMarkup ? 'M' : 'X'), includeIDs);
	}
	
	/**
	 * Write the data from a table to a writer in some column based format like
	 * CSV. If the argument separator is the comma or semicolon, field values
	 * are enclosed in double quotes.
	 * @param table the table whose data to write
	 * @param separator the separator to use
	 * @param out the writer to write to
	 */
	public static void writeTableData(ImRegion table, char separator, Writer out) throws IOException {
		writeTableData(table, separator, false, out);
	}
	private static void writeTableData(ImRegion table, char separator, boolean includeIDs, Writer out) throws IOException {
		writeTableOrTableGridData(table, false, separator, includeIDs, out);
	}
	
	/**
	 * Write the data from a table to a writer in XML, specifically in XHTML.
	 * @param table the table whose data to write
	 * @param out the writer to write to
	 */
	public static void writeTableDataXml(ImRegion table, Writer out) throws IOException {
		writeTableDataXml(table, false, false, out);
	}
	
	/**
	 * Write the data from a table to a writer in XML, specifically in XHTML.
	 * If <code>includeMarkup</code> argument is true, this method also outputs
	 * any annotation present inside table cells, which might result in output
	 * that does not conform to the definition of XHTML in a strict sense, as
	 * table cells an contain arbitrary XML elements.
	 * @param table the table whose data to write
	 * @param includeMarkup add XML markup inside table cells?
	 * @param out the writer to write to
	 */
	public static void writeTableDataXml(ImRegion table, boolean includeMarkup, Writer out) throws IOException {
		writeTableDataXml(table, includeMarkup, false, out);
	}
	
	/**
	 * Write the data from a table to a writer in XML, specifically in XHTML.
	 * If <code>includeMarkup</code> argument is true, this method also outputs
	 * any annotation present inside table cells, which might result in output
	 * that does not conform to the definition of XHTML in a strict sense, as
	 * table cells an contain arbitrary XML elements.
	 * @param table the table whose data to write
	 * @param includeMarkup add XML markup inside table cells?
	 * @param includeIDs include IDs of XML elements?
	 * @param out the writer to write to
	 */
	public static void writeTableDataXml(ImRegion table, boolean includeMarkup, boolean includeIDs, Writer out) throws IOException {
		writeTableData(table, (includeMarkup ? 'M' : 'X'), includeIDs, out);
	}
	
	/**
	 * Copy the data from a whole grid of connected tables in some column based
	 * format like CSV. If the argument separator is the comma or semicolon,
	 * field values are enclosed in double quotes.
	 * @param table the table whose data to copy
	 * @param separator the separator to use
	 * @return the data from the argument table
	 */
	public static String getTableGridData(ImRegion table, char separator) {
		return getTableGridData(table, separator, false);
	}
	private static String getTableGridData(ImRegion table, char separator, boolean includeIDs) {
		return getTableOrTableGridDataString(table, true, separator, includeIDs);
	}
	
	/**
	 * Copy the data from a whole grid of connected tables in XML, specifically
	 * in XHTML.
	 * @param table the table whose data to copy
	 * @return the data from the argument table
	 */
	public static String getTableGridDataXml(ImRegion table) {
		return getTableGridDataXml(table, false, false);
	}
	
	/**
	 * Copy the data from a whole grid of connected tables in XML, specifically
	 * in XHTML. If <code>includeMarkup</code> argument is true, this method 
	 * also outputs any annotation present inside table cells, which might
	 * result in output that does not conform to the definition of XHTML in a
	 * strict sense, as table cells an contain arbitrary XML elements.
	 * @param table the table whose data to copy
	 * @param includeMarkup add XML markup inside table cells?
	 * @return the data from the argument table
	 */
	public static String getTableGridDataXml(ImRegion table, boolean includeMarkup) {
		return getTableGridDataXml(table, includeMarkup, false);
	}
	
	/**
	 * Copy the data from a whole grid of connected tables in XML, specifically
	 * in XHTML. If <code>includeMarkup</code> argument is true, this method 
	 * also outputs any annotation present inside table cells, which might
	 * result in output that does not conform to the definition of XHTML in a
	 * strict sense, as table cells an contain arbitrary XML elements.
	 * @param table the table whose data to copy
	 * @param includeMarkup add XML markup inside table cells?
	 * @param includeIDs include IDs of XML elements?
	 * @return the data from the argument table
	 */
	public static String getTableGridDataXml(ImRegion table, boolean includeMarkup, boolean includeIDs) {
		return getTableGridData(table, (includeMarkup ? 'M' : 'X'), includeIDs);
	}
	
	/**
	 * Write the data from a whole grid of connected tables to a writer in some
	 * column based format like CSV. If the argument separator is the comma or
	 * semicolon, field values are enclosed in double quotes.
	 * @param table the table whose data to write
	 * @param separator the separator to use
	 * @param out the writer to write to
	 */
	public static void writeTableGridData(ImRegion table, char separator, Writer out) throws IOException {
		writeTableGridData(table, separator, false, out);
	}
	private static void writeTableGridData(ImRegion table, char separator, boolean includeIDs, Writer out) throws IOException {
		writeTableOrTableGridData(table, true, separator, includeIDs, out);
	}
	
	/**
	 * Write the data from a whole grid of connected tables to a writer in XML,
	 * specifically in XHTML.
	 * @param table the table whose data to write
	 * @param out the writer to write to
	 */
	public static void writeTableGridDataXml(ImRegion table, Writer out) throws IOException {
		writeTableGridDataXml(table, false, false, out);
	}
	
	/**
	 * Write the data from a whole grid of connected tables to a writer in XML,
	 * specifically in XHTML. If <code>includeMarkup</code> argument is true,
	 * this method also outputs any annotation present inside table cells,
	 * which might result in output that does not conform to the definition of
	 * XHTML in a strict sense, as table cells an contain arbitrary XML
	 * elements.
	 * @param table the table whose data to write
	 * @param includeMarkup add XML markup inside table cells?
	 * @param out the writer to write to
	 */
	public static void writeTableGridDataXml(ImRegion table, boolean includeMarkup, Writer out) throws IOException {
		writeTableGridDataXml(table, includeMarkup, false, out);
	}
	
	/**
	 * Write the data from a whole grid of connected tables to a writer in XML,
	 * specifically in XHTML. If <code>includeMarkup</code> argument is true,
	 * this method also outputs any annotation present inside table cells,
	 * which might result in output that does not conform to the definition of
	 * XHTML in a strict sense, as table cells an contain arbitrary XML
	 * elements.
	 * @param table the table whose data to write
	 * @param includeMarkup add XML markup inside table cells?
	 * @param includeIDs include IDs of XML elements?
	 * @param out the writer to write to
	 */
	public static void writeTableGridDataXml(ImRegion table, boolean includeMarkup, boolean includeIDs, Writer out) throws IOException {
		writeTableGridData(table, (includeMarkup ? 'M' : 'X'), includeIDs, out);
	}
	
	private static String getTableOrTableGridDataString(ImRegion table, boolean fullGrid, char separator, boolean includeIDs) {
		try {
			StringWriter sw = new StringWriter();
			writeTableOrTableGridData(table, fullGrid, separator, includeIDs, sw);
			return sw.toString();
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			return null; // never gonna happen with StringWriter, but Java don't know
		}
	}
	
	private static void writeTableOrTableGridData(ImRegion table, boolean fullGrid, char separator, boolean includeIDs, Writer out) throws IOException {
		
		//	check for detail XML markup
		boolean includeMarkup = false;
		if (separator == 'M') {
			separator = 'X';
			includeMarkup = true;
		}
//		
//		//	get tables
//		ImRegion[][] tables = {{table}};
//		if (fullGrid) // get whole grid of connected tables if requested
//			tables = getConnectedTables(table);
//		
//		//	get all rows, columns, and cells for all the tables up front
//		ImRegion[][][] tableRows = new ImRegion[tables.length][][];
//		ImRegion[][][] tableCols = new ImRegion[tables.length][][];
//		ImRegion[][][][] tableCells = new ImRegion[tables.length][][][];
//		
//		//	count out header rows and label columns per table
//		int[][] tableHeaderRowCount = new int[tables.length][];
//		int[][] tableLabelColCount = new int[tables.length][];
//		
//		//	populate arrays
//		int gridRowCount = 0;
//		int gridColCount = 0;
//		for (int ty = 0; ty < tables.length; ty++) {
//			tableRows[ty] = new ImRegion[tables[ty].length][];
//			tableCols[ty] = new ImRegion[tables[ty].length][];
//			tableCells[ty] = new ImRegion[tables[ty].length][][];
//			tableHeaderRowCount[ty] = new int[tables[ty].length];
//			tableLabelColCount[ty] = new int[tables[ty].length];
//			for (int tx = 0; tx < tables[ty].length; tx++) {
//				
//				//	get backing page
//				ImPage page = tables[ty][tx].getPage();
//				
//				//	get columns and rows of current table (getting cells sorts arrays based on text direction)
//				ImRegion[] rows = page.getRegionsInside(ImRegion.TABLE_ROW_TYPE, tables[ty][tx].bounds, false);
//				ImRegion[] cols = page.getRegionsInside(ImRegion.TABLE_COL_TYPE, tables[ty][tx].bounds, false);
//				
//				//	get cells (sorts columns and rows based upon predominant text direction)
//				ImRegion[][] cells = getTableCells(tables[ty][tx], null, rows, cols, false);
//				
//				//	count out or copy along number of header rows
//				if (tx == 0) {
//					tableHeaderRowCount[ty][tx] = 0;
//					boolean inHeaderRows = true;
//					for (int r = 0; r < rows.length; r++) {
//						if ((ty == 0) && (r == 0))
//							tableHeaderRowCount[ty][tx]++;
//						else if (inHeaderRows && rows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
//							tableHeaderRowCount[ty][tx]++;
//						else if (inHeaderRows && (ty != 0) && (r < tableHeaderRowCount[0][tx]) && isCellWiseFuzzyMatch(cells, page, r, -1, tableCells[0][tx], tables[0][tx].getPage()))
//							tableHeaderRowCount[ty][tx]++;
//						else inHeaderRows = false;
//					}
//					gridRowCount += rows.length;
//					if (ty != 0)
//						gridRowCount -= tableHeaderRowCount[ty][tx];
//				}
//				else tableHeaderRowCount[ty][tx] = tableHeaderRowCount[ty][tx-1];
//				
//				//	count out or copy along number of label columns
//				if (ty == 0) {
//					tableLabelColCount[ty][tx] = 0;
//					boolean inLabelCols = true;
//					for (int c = 0; c < cols.length; c++) {
//						if ((tx == 0) && (c == 0))
//							tableLabelColCount[ty][tx]++;
//						else if (inLabelCols && cols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
//							tableLabelColCount[ty][tx]++;
//						else if (inLabelCols && (tx != 0) && (c < tableLabelColCount[ty][0]) && isCellWiseFuzzyMatch(cells, page, -1, c, tableCells[ty][0], tables[ty][0].getPage()))
//							tableLabelColCount[ty][tx]++;
//						else inLabelCols = false;
//					}
//					gridColCount += cols.length;
//					if (tx != 0)
//						gridColCount -= tableLabelColCount[ty][tx];
//				}
//				else tableLabelColCount[ty][tx] = tableLabelColCount[ty-1][tx];
//				
//				//	store regions in overall arrays
//				tableRows[ty][tx] = rows;
//				tableCols[ty][tx] = cols;
//				tableCells[ty][tx] = cells;
//			}
//		}
////		
////		//	count out overall size of whole grid
////		int gridRows = 0;
////		int gridCols = 0;
////		for (int ty = 0; ty < tables.length; ty++) {
////			for (int tx = 0; tx < tables[ty].length; tx++) {
////				
////				//	get text direction
////				String textDirection = ((String) tables[ty][tx].getAttribute(ImRegion.TEXT_DIRECTION_ATTRIBUTE, ImRegion.TEXT_DIRECTION_LEFT_RIGHT));
////				
////				//	count rows of current table (down left edge of grid)
////				if (tx == 0) {
////					ImRegion[] rows = tables[ty][tx].getPage().getRegionsInside(ImRegion.TABLE_ROW_TYPE, tables[ty][tx].bounds, false);
////					Arrays.sort(rows, getTableRowOrder(textDirection));
////					if (ty == 0)
////						gridRows += rows.length; // count header rows at top of grid
////					else for (int r = 1; r < rows.length; r++) {
////						if (!rows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
////							gridRows++;
////					}
////				}
////				
////				//	count columns of current table (across top of grid)
////				if (ty == 0) {
////					ImRegion[] cols = tables[ty][tx].getPage().getRegionsInside(ImRegion.TABLE_COL_TYPE, tables[ty][tx].bounds, false);
////					Arrays.sort(cols, getTableColumnOrder(textDirection));
////					if (tx == 0)
////						gridCols += cols.length; // count label columns at left edge of grid
////					else for (int c = 1; c < cols.length; c++) {
////						if (!cols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
////							gridCols++;
////					}
////				}
////			}
////		}
//		
//		//	tray up cells for whole grid
//		TableCell[][] gridCells = new TableCell[gridRowCount][gridColCount];
//		ImRegion[] gridRows = new ImRegion[gridRowCount];
//		int gridY = 0;
//		for (int ty = 0; ty < tables.length; ty++) {
//			int gridX = 0;
//			int maxTableY = 0;
//			
//			//	process grid row left to right
//			for (int tx = 0; tx < tables[ty].length; tx++) {
////				
////				//	get backing page
////				ImPage page = tables[ty][tx].getPage();
//				
//				//	get columns and rows of current table (getting cells sorts arrays based on text direction)
////				ImRegion[] rows = page.getRegionsInside(ImRegion.TABLE_ROW_TYPE, tables[ty][tx].bounds, false);
//				ImRegion[] rows = tableRows[ty][tx];
////				ImRegion[] cols = page.getRegionsInside(ImRegion.TABLE_COL_TYPE, tables[ty][tx].bounds, false);
//				ImRegion[] cols = tableCols[ty][tx];
//				
//				//	get cells (sorts columns and rows based upon predominant text direction)
////				ImRegion[][] cells = getTableCells(tables[ty][tx], null, rows, cols, false);
//				ImRegion[][] cells = tableCells[ty][tx];
//				
//				//	get backing page
//				ImPage page = tables[ty][tx].getPage();
//				
//				//	count coordinates individually for each table
//				int tableY = 0;
//				int maxTableX = 0;
//				for (int r = 0; r < rows.length; r++) {
////					if ((ty != 0) && ((r == 0) || rows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE)))
////						continue; // skip over column headers unless on top edge of grid
//					if ((ty != 0) && (r < tableHeaderRowCount[ty][tx]))
//						continue; // skip over column headers unless on top edge of grid
//					
//					//	store (first) row region (mainly for UUID)
//					if (tx == 0)
//						gridRows[gridY + tableY] = rows[r];
//					
//					//	add row data
//					int tableX = 0;
//					for (int c = 0; c < cols.length; c++) {
////						if ((tx != 0) && ((c == 0) || cols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE)))
////							continue; // skip over row lables unless on left edge of whole grid
//						if ((tx != 0) && (c < tableLabelColCount[ty][tx]))
//							continue; // skip over row lables unless on left edge of whole grid
//						
//						//	cell reaching in from left
//						if ((c != 0) && (cells[r][c] == cells[r][c-1])) {
//							gridCells[gridY + tableY][gridX + tableX] = gridCells[gridY + tableY][gridX + tableX - 1];
//							if (gridCells[gridY + tableY][gridX + tableX].rowSpan == 1) // only expand colspan across top row of cells
//								gridCells[gridY + tableY][gridX + tableX].colSpan++;
//						}
//						
//						//	cell reaching in from top
//						else if ((r != 0) && (cells[r][c] == cells[r-1][c])) {
//							gridCells[gridY + tableY][gridX + tableX] = gridCells[gridY + tableY - 1][gridX + tableX];
//							gridCells[gridY + tableY][gridX + tableX].rowSpan++;
//						}
//						
//						//	(top left corner of) new cell
//						else {
//							boolean isHeader = (
//									(r < tableHeaderRowCount[ty][tx]) // column headers
//									||
//									(c < tableLabelColCount[ty][tx]) // row labels
//								);
//							gridCells[gridY + tableY][gridX + tableX] = new TableCell(page, cells[r][c], isHeader);
//						}
//						
//						//	switch to next column of overall grid
//						tableX++;
//					}
//					
//					//	remember width of current row, and switch to next row of overall grid
//					maxTableX = Math.max(maxTableX, tableX);
//					tableY++;
//				}
//				
//				//	remember height of current table, and switch to next table in grid
//				maxTableY = Math.max(maxTableY, tableY);
//				gridX += maxTableX;
//			}
//			
//			//	switch to next row of grid tables
//			gridY += maxTableY;
//		}
//		
//		//	output overall table row by row
//		if (separator == 'X')
//			out.write("<table" + (includeIDs ? (" id=\"" + tables[0][0].getUUID() + "\"") : "") + ">\r\n");
//		for (int r = 0; r < gridCells.length; r++) {
//			if (separator == 'X')
//				out.write("<tr" + (includeIDs ? (" id=\"" + gridRows[r].getUUID() + "\"") : "") + ">\r\n");
//			for (int c = 0; c < gridCells[r].length; c++) {
//				if (separator == 'X') {
//					if ((c != 0) && (gridCells[r][c] == gridCells[r][c-1]))
//						continue;
//					if ((r != 0) && (gridCells[r][c] == gridCells[r-1][c]))
//						continue;
//					writeCellData(gridCells[r][c], separator, includeMarkup, includeIDs, out);
//				}
//				else {
//					writeCellData(gridCells[r][c], separator, false, false, out);
//					if ((c+1) < gridCells[r].length)
//						out.write("" + separator);
//				}
//			}
//			if (separator == 'X')
//				out.write("</tr>");
//			out.write("\r\n");
//		}
//		if (separator == 'X')
//			out.write("</table>\r\n");
		
		//	tray up table or table grid
		ImTable imt = wrapTable(table, fullGrid);
		
		//	output overall table row by row
		if (separator == 'X') {
			out.write("<table" + (includeIDs ? (" id=\"" + imt.tableRegions[0][0].getUUID() + "\"") : "") + ">");
			writeNewline(out);
		}
		for (int r = 0; r < imt.rows.length; r++) {
			if (separator == 'X') {
				out.write("<tr" + (includeIDs ? (" id=\"" + imt.rows[r].rowRegions[0].getUUID() + "\"") : "") + ">");
				writeNewline(out);
			}
			for (int c = 0; c < imt.cols.length; c++) {
				ImTableCell imtc = imt.cells[r][c];
				if (separator == 'X') {
					if (imtc.c < c)
						continue;
					if (imtc.r < r)
						continue;
					writeCellData(imtc, separator, includeMarkup, includeIDs, out);
				}
				else {
					writeCellData(imtc, separator, false, false, out);
					if ((c+1) < imt.cols.length)
						out.write("" + separator);
				}
			}
			if (separator == 'X')
				out.write("</tr>");
			writeNewline(out);
		}
		if (separator == 'X') {
			out.write("</table>");
			writeNewline(out);
		}
		
		//	finally ...
		out.flush();
	}
	private static void writeNewline(Writer out) throws IOException {
		if (out instanceof BufferedWriter)
			((BufferedWriter) out).newLine();
		else out.write("\r\n");
	}
	
	private static boolean isCellWiseFuzzyMatch(ImRegion[][] cells, ImPage cellPage, int r, int c, ImRegion[][] cCells, ImPage cCellPage) {
		
		//	extract strings to compare, and check edge cases along the way
		String[] cellStrings;
		String[] cCellStrings;
		if ((c < 0) && (r < 0))
			return false;
		else if (c < 0) /* check across row r */ {
			if ((cells.length <= r) || (cCells.length <= r))
				return false;
			if (cells[r].length != cCells[r].length)
				return false;
			if (cells[r].length == 0)
				return true; // no columns to check
			cellStrings = new String[cells[r].length];
			cCellStrings = new String[cells[r].length];
			for (c = 0; c < cells[r].length; c++) {
				if ((c != 0) && ((cells[r][c] == cells[r][c-1]) != (cCells[r][c] == cCells[r][c-1])))
					return false; // cells connected in one array, but not in other
				if ((c != 0) && (cells[r][c] == cells[r][c-1])) /* cells connected, no need to compare again */ {
					cellStrings[c] = null;
					cCellStrings[c] = null;
				}
				else {
					cellStrings[c] = getCellString(cells[r][c], cellPage);
					cCellStrings[c] = getCellString(cCells[r][c], cCellPage);
				}
			}
		}
		else if (r < 0) /* check down column c */ {
			if (cells.length != cCells.length)
				return false;
			if (cells.length == 0)
				return true; // no rows to check
			if ((cells[0].length <= c) || (cCells[0].length <= c))
				return false;
			cellStrings = new String[cells.length];
			cCellStrings = new String[cells.length];
			for (r = 0; r < cells.length; r++) {
				if ((r != 0) && ((cells[r][c] == cells[r-1][c]) != (cCells[r][c] == cCells[r-1][c])))
					return false; // cells connected in one array, but not in other
				if ((r != 0) && (cells[r][c] == cells[r-1][c])) /* cells connected, no need to compare again */  {
					cellStrings[r] = null;
					cCellStrings[r] = null;
				}
				else {
					cellStrings[r] = getCellString(cells[r][c], cellPage);
					cCellStrings[r] = getCellString(cCells[r][c], cCellPage);
				}
			}
		}
		else return false;
		
		//	compare strings
		int compCount = 0;
		int matchCount = 0;
		for (int s = 0; s < cellStrings.length; s++) {
			if (cellStrings[s] == null)
				continue; // cell same as on left or above
			compCount++;
			if (cellStrings[s].equals(cCellStrings[s]))
				matchCount++;
		}
		return ((compCount * 2) < (matchCount * 3)); // require mor than two thirds of cells to be equal for overall match
	}
	private static String getCellString(ImRegion cell, ImPage page) {
		ImWord[] cellWords;
		if (cell.getPage() == null)
			cellWords = page.getWordsInside(cell.bounds);
		else cellWords = cell.getWords();
		if (cellWords.length == 0)
			return "";
		Arrays.sort(cellWords, textStreamOrder);
		return getString(cellWords[0], cellWords[cellWords.length-1], true);
	}
	
	/**
	 * Wrap a table of table grid into a single dedicated table object for
	 * unified access in style of a table. This might be easier and more
	 * natural to handle than a table represented by technically distinct
	 * <code>ImRegion</code> objects in a given page.
	 * @param table the table region to start from
	 * @param fullGrid include connected tables?
	 * @return the table wrapper object
	 */
	public static ImTable wrapTable(ImRegion table, boolean fullGrid) {
		
		//	get tables
		ImRegion[][] tables = {{table}};
		if (fullGrid) // get whole grid of connected tables if requested
			tables = getConnectedTables(table);
		
		//	get all rows, columns, and cells for all the tables up front
		ImRegion[][][] tableRows = new ImRegion[tables.length][][];
		ImRegion[][][] tableCols = new ImRegion[tables.length][][];
		ImRegion[][][][] tableCells = new ImRegion[tables.length][][][];
		
		//	count out header rows and label columns per table
		int[][] tableHeaderRowCount = new int[tables.length][];
		int[][] tableLabelColCount = new int[tables.length][];
		
		//	populate arrays
		int gridRowCount = 0;
		int gridColCount = 0;
		for (int ty = 0; ty < tables.length; ty++) {
			tableRows[ty] = new ImRegion[tables[ty].length][];
			tableCols[ty] = new ImRegion[tables[ty].length][];
			tableCells[ty] = new ImRegion[tables[ty].length][][];
			tableHeaderRowCount[ty] = new int[tables[ty].length];
			tableLabelColCount[ty] = new int[tables[ty].length];
			for (int tx = 0; tx < tables[ty].length; tx++) {
				
				//	get backing page
				ImPage page = tables[ty][tx].getPage();
				
				//	get columns and rows of current table (getting cells sorts arrays based on text direction)
				ImRegion[] rows = page.getRegionsInside(ImRegion.TABLE_ROW_TYPE, tables[ty][tx].bounds, false);
				ImRegion[] cols = page.getRegionsInside(ImRegion.TABLE_COL_TYPE, tables[ty][tx].bounds, false);
				
				//	get cells (sorts columns and rows based upon predominant text direction)
				ImRegion[][] cells = getTableCells(tables[ty][tx], null, rows, cols, false);
				
				//	count out or copy along number of header rows
				if (tx == 0) {
					tableHeaderRowCount[ty][tx] = 0;
					boolean inHeaderRows = true;
					for (int r = 0; r < rows.length; r++) {
						if ((ty == 0) && (r == 0))
							tableHeaderRowCount[ty][tx]++;
						else if (inHeaderRows && rows[r].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
							tableHeaderRowCount[ty][tx]++;
						else if (inHeaderRows && (ty != 0) && (r < tableHeaderRowCount[0][tx]) && isCellWiseFuzzyMatch(cells, page, r, -1, tableCells[0][tx], tables[0][tx].getPage()))
							tableHeaderRowCount[ty][tx]++;
						else inHeaderRows = false;
					}
					gridRowCount += rows.length;
					if (ty != 0)
						gridRowCount -= tableHeaderRowCount[ty][tx];
				}
				else tableHeaderRowCount[ty][tx] = tableHeaderRowCount[ty][tx-1];
				
				//	count out or copy along number of label columns
				if (ty == 0) {
					tableLabelColCount[ty][tx] = 0;
					boolean inLabelCols = true;
					for (int c = 0; c < cols.length; c++) {
						if ((tx == 0) && (c == 0))
							tableLabelColCount[ty][tx]++;
						else if (inLabelCols && cols[c].hasAttribute(ImRegion.TABLE_HEADER_MARKER_ATTRIBUTE))
							tableLabelColCount[ty][tx]++;
						else if (inLabelCols && (tx != 0) && (c < tableLabelColCount[ty][0]) && isCellWiseFuzzyMatch(cells, page, -1, c, tableCells[ty][0], tables[ty][0].getPage()))
							tableLabelColCount[ty][tx]++;
						else inLabelCols = false;
					}
					gridColCount += cols.length;
					if (tx != 0)
						gridColCount -= tableLabelColCount[ty][tx];
				}
				else tableLabelColCount[ty][tx] = tableLabelColCount[ty-1][tx];
				
				//	store regions in overall arrays
				tableRows[ty][tx] = rows;
				tableCols[ty][tx] = cols;
				tableCells[ty][tx] = cells;
			}
		}
		
		//	tray up rows and columns
		ImTableColumn[] gridCols = new ImTableColumn[gridColCount];
		ImTableRow[] gridRows = new ImTableRow[gridRowCount];
		
		//	tray up cells for whole grid
		ImTableCell[][] gridCells = new ImTableCell[gridRowCount][gridColCount];
		int gridY = 0;
		for (int ty = 0; ty < tables.length; ty++) {
			int gridX = 0;
			int maxTableY = 0;
			
			//	process grid row left to right
			for (int tx = 0; tx < tables[ty].length; tx++) {
				
				//	get columns and rows of current table (getting cells sorts arrays based on text direction)
				ImRegion[] rows = tableRows[ty][tx];
				ImRegion[] cols = tableCols[ty][tx];
				
				//	get cells (sorts columns and rows based upon predominant text direction)
				ImRegion[][] cells = tableCells[ty][tx];
				
				//	get backing page
				ImPage page = tables[ty][tx].getPage();
				
				//	count coordinates individually for each table
				int tableY = 0;
				int maxTableX = 0;
				for (int r = 0; r < rows.length; r++) {
					if ((ty != 0) && (r < tableHeaderRowCount[ty][tx]))
						continue; // skip over column headers unless on top edge of grid
					
					//	store row regions
					if (tx == 0)
						gridRows[gridY + tableY] = new ImTableRow(new ImRegion[tables[ty].length], (gridY + tableY));
					gridRows[gridY + tableY].rowRegions[tx] = rows[r];
					
					//	add row data
					int tableX = 0;
					for (int c = 0; c < cols.length; c++) {
						if ((tx != 0) && (c < tableLabelColCount[ty][tx]))
							continue; // skip over row lables unless on left edge of whole grid
						
						//	store column regions
						if ((ty == 0) && (r == 0))
							gridCols[gridX + tableX] = new ImTableColumn(new ImRegion[tables.length], (gridX + tableX));
						gridCols[gridX + tableX].columnRegions[ty] = cols[c];
						
						//	cell reaching in from left
						if ((c != 0) && (cells[r][c] == cells[r][c-1])) {
							gridCells[gridY + tableY][gridX + tableX] = gridCells[gridY + tableY][gridX + tableX - 1];
							if (gridCells[gridY + tableY][gridX + tableX].rowSpan == 1) // only expand colspan across top row of cells
								gridCells[gridY + tableY][gridX + tableX].colSpan++;
						}
						
						//	cell reaching in from top
						else if ((r != 0) && (cells[r][c] == cells[r-1][c])) {
							gridCells[gridY + tableY][gridX + tableX] = gridCells[gridY + tableY - 1][gridX + tableX];
							gridCells[gridY + tableY][gridX + tableX].rowSpan++;
						}
						
						//	(top left corner of) new cell
						else {
							boolean isHeader = (
									(r < tableHeaderRowCount[ty][tx]) // column headers
									||
									(c < tableLabelColCount[ty][tx]) // row labels
								);
							gridCells[gridY + tableY][gridX + tableX] = new ImTableCell(page, cells[r][c], isHeader, (gridX + tableX), (gridY + tableY));
						}
						
						//	switch to next column of overall grid
						tableX++;
					}
					
					//	remember width of current row, and switch to next row of overall grid
					maxTableX = Math.max(maxTableX, tableX);
					tableY++;
				}
				
				//	remember height of current table, and switch to next table in grid
				maxTableY = Math.max(maxTableY, tableY);
				gridX += maxTableX;
			}
			
			//	switch to next row of grid tables
			gridY += maxTableY;
		}
		
		//	wrap up whole thing
		return new ImTable(table.getDocument(), tables, gridCols, gridRows, gridCells);
	}
	
	/* TODO create public table model:
	 * - expose contents of cells as EditableAnnotation (created on demand as single-cell ImDocumentRoots)
	 * - make whole thing implement interfaces of more generic GAMTA table model ...
	 * - ... and create latter first to support display facilities
	 */
	
	/**
	 * Wrapper for a table represented by <code>ImRegion</code> objects in one
	 * or more pages of an Image Markup documents. Instances of this class
	 * provide a more natural way of accessing the data in a table than the
	 * generic representation with <code>ImRegion</code>, especially for grids
	 * of multiple connected tables.
	 * 
	 * @author sautter
	 */
	public static class ImTable {
		
		/** the document the table belongs to */
		public final ImDocument doc;
		
		/** the underlying table regions as connected to a grid (rows are the outer dimension of the array) */
		public final ImRegion[][] tableRegions; // regions are across grid rows (y in outer dimension)
		
		ImTableColumn[] cols;
		ImTableRow[] rows;
		ImTableCell[][] cells; // cells are across rows (row index in outer dimension)
		
		ImTable(ImDocument doc, ImRegion[][] tableRegions, ImTableColumn[] cols, ImTableRow[] rows, ImTableCell[][] cells) {
			this.doc = doc;
			this.tableRegions = tableRegions;
			this.cols = cols;
			this.rows = rows;
			this.cells = cells;
			boolean inRowLabels = true;
			for (int c = 0; c < this.cols.length; c++) {
				this.cols[c].table = this;
				if (inRowLabels) {
					int headerCellCount = 0;
					for (int r = 0; r < this.rows.length; r++) {
						if (this.cells[r][c].isHeader)
							headerCellCount++;
					}
					if (this.rows.length < (headerCellCount * 2))
						this.cols[c].isRowLabelColumn = true;
					else inRowLabels = false;
				}
			}
			boolean inColumnHeaders = true;
			for (int r = 0; r < this.rows.length; r++) {
				this.rows[r].table = this;
				if (inColumnHeaders) {
					int headerCellCount = 0;
					for (int c = 0; c < this.cols.length; c++) {
						if (this.cells[r][c].isHeader)
							headerCellCount++;
					}
					if (this.cols.length < (headerCellCount * 2))
						this.rows[r].isColumnHeaderRow = true;
					else inColumnHeaders = false;
				}
			}
			for (int r = 0; r < this.rows.length; r++) {
				for (int c = 0; c < this.cols.length; c++)
					this.cells[r][c].table = this;
			}
		}
		
		/**
		 * Retrieve the number of columns in the table.
		 * @return the number of columns
		 */
		public int getColumnCount() {
			return this.cols.length;
		}
		
		/**
		 * Retrieve the column at position <code>c</code>.
		 * @param c the column index to access
		 * @return the column at the specified position
		 */
		public ImTableColumn getColumnAt(int c) {
			return this.cols[c];
		}
		
		/**
		 * Retrieve the number of rows in the table.
		 * @return the number of rows
		 */
		public int getRowCount() {
			return this.rows.length;
		}
		
		/**
		 * Retrieve the row at position <code>r</code>.
		 * @param r the row index to access
		 * @return the row at the specified position
		 */
		public ImTableRow getRowAt(int r) {
			return this.rows[r];
		}
		
		/**
		 * Retrieve the cell at position <code>c</code>, <code>r</code>.
		 * @param c the column index to access
		 * @param r the row index to access
		 * @return the cell at the specified position
		 */
		public ImTableCell getCellAt(int c, int r) {
			return this.cells[r][c]; // cells are in rows
		}
		
		/**
		 * Retrieve the table region that contains the table cell at position
		 * <code>c</code>, <code>r</code>.
		 * @param c the column index of the cell
		 * @param r the row index of the cell
		 * @return the table region at the specified position
		 */
		public ImRegion getTableRegionAt(int c, int r) {
			ImTableCell cell = this.getCellAt(c, r);
			for (int ty = 0; ty < this.tableRegions.length; ty++)
				for (int tx = 0; tx < this.tableRegions[ty].length; tx++) {
					if ((this.tableRegions[ty][tx].pageId == cell.cellRegion.pageId) && this.tableRegions[ty][tx].bounds.includes(cell.cellRegion.bounds, false))
						return this.tableRegions[ty][tx];
				}
			return null; // should never happen unless something is wrong with the undelying region structure
		}
		
		/**
		 * Retrieve the table column region that contains the table cell at
		 * position <code>c</code>, <code>r</code>.
		 * @param c the column index of the cell
		 * @param r the row index of the cell
		 * @return the table column region at the specified position
		 */
		public ImRegion getColumnRegionAt(int c, int r) {
			return this.getColumnAt(c).getRegionAt(r);
		}
		
		/**
		 * Retrieve the table row region that contains the table cell at
		 * position <code>c</code>, <code>r</code>.
		 * @param c the column index of the cell
		 * @param r the row index of the cell
		 * @return the table row region at the specified position
		 */
		public ImRegion getRowRegionAt(int c, int r) {
			return this.getRowAt(r).getRegionAt(c);
		}
	}
	
//	public static void main(String[] args) throws Exception {
//		ImDocument doc = ImDocumentIO.loadDocument(new File("E:/Testdaten/PdfExtract/zt00872.pdf.tables.imdir"));
//		ImPage page = doc.getPage(8);
//		ImRegion[] tables = page.getRegions(ImRegion.TABLE_TYPE);
//		Arrays.sort(tables, topDownOrder);
//		System.out.println("Get + println");
//		for (int t = 0; t < tables.length; t++) {
////			System.out.println(getTableData(tables[t], ';'));
////			System.out.println(getTableData(tables[t], '\t'));
//			System.out.println("Testing " + tables[t].getLocalID());
//			ImTable table = wrapTable(tables[t], false);
//			System.out.println(" - got " + table.cols.length + " columns");
//			for (int c = 0; c < table.cols.length; c++)
//				System.out.println("   - column " + c + " has " + table.cols[c].cols.length + " regions: " + Arrays.toString(table.cols[c].cols));
//			System.out.println(" - got " + table.rows.length + " rows");
//			for (int r = 0; r < table.rows.length; r++)
//				System.out.println("   - row " + r + " has " + table.rows[r].rows.length + " regions: " + Arrays.toString(table.rows[r].rows));
//		}
////		System.out.println("Write");
////		for (int t = 0; t < tables.length; t++)
////			writeTableData(tables[t], 'X', new PrintWriter(System.out, true));
//		System.out.println();
//		System.out.println("Get + println");
//		for (int t = 0; t < tables.length; t++) {
//			if (tables[t].hasAttribute("rowsContinueFrom"))
//				continue;
//			if (tables[t].hasAttribute("colsContinueFrom"))
//				continue;
////			System.out.println(getTableGridData(tables[t], ','));
////			System.out.println(getTableGridData(tables[t], '\t'));
//			System.out.println("Testing grid around " + tables[t].getLocalID());
//			ImTable table = wrapTable(tables[t], true);
//			System.out.println(" - got " + table.cols.length + " columns");
//			for (int c = 0; c < table.cols.length; c++)
//				System.out.println("   - column " + c + " has " + table.cols[c].cols.length + " regions: " + Arrays.toString(table.cols[c].cols));
//			System.out.println(" - got " + table.rows.length + " rows");
//			for (int r = 0; r < table.rows.length; r++)
//				System.out.println("   - row " + r + " has " + table.rows[r].rows.length + " regions: " + Arrays.toString(table.rows[r].rows));
//		}
////		System.out.println("Write");
////		for (int t = 0; t < tables.length; t++) {
////			if (tables[t].hasAttribute("rowsContinueFrom"))
////				continue;
////			if (tables[t].hasAttribute("colsContinueFrom"))
////				continue;
////			writeTableGridData(tables[t], 'X', new PrintWriter(System.out, true));
////		}
//	}
	
	/**
	 * A column in an Image Markup table wrapper.
	 * 
	 * @author sautter
	 */
	public static class ImTableColumn {
		
		/** the underlying table column regions (one for each vertically connected table region) */
		public final ImRegion[] columnRegions;
		
		ImTable table;
		boolean isRowLabelColumn;
		int c;
		
		ImTableColumn(ImRegion[] columnRegions, int c) {
			this.columnRegions = columnRegions;
			this.c = c;
		}
		
		/**
		 * Retrieve the table the column belongs to.
		 * @return the parent table
		 */
		public ImTable getTable() {
			return this.table;
		}
		
		/**
		 * Retrieve the horizontal position of the column across the table,
		 * i.e., its column index.
		 * @return the horizontal position
		 */
		public int getPosition() {
			return this.c;
		}
		
		/**
		 * Test whether or not the column contains row label cells.
		 * @return true if the column contains row labels
		 */
		public boolean isRowLabelColumn() {
			return this.isRowLabelColumn;
		}
		
		/**
		 * Retrieve the cell at position <code>r</code> down the column.
		 * @param r the row index to access
		 * @return the cell at the specified position
		 */
		public ImTableCell getCellAt(int r) {
			return this.table.cells[r][this.c]; // cells are in rows
		}
		
		/**
		 * Retrieve the table column region that contains the table cell at
		 * position <code>r</code>.
		 * @param r the row index of the cell
		 * @return the table column region at the specified position
		 */
		public ImRegion getRegionAt(int r) {
			return this.getRegionFor(this.getCellAt(r).cellRegion);
		}
		ImRegion getRegionFor(ImRegion cellRegion) {
			int maxOverlap = 0;
			ImRegion maxOverlapColumnRegion = null;
			for (int cr = 0; cr < this.columnRegions.length; cr++) {
				if (this.columnRegions[cr].pageId != cellRegion.pageId)
					continue;
				if (this.columnRegions[cr].bounds.includes(cellRegion.bounds, false))
					return this.columnRegions[cr];
				if (!this.columnRegions[cr].bounds.overlaps(cellRegion.bounds))
					continue;
				int overlap = this.columnRegions[cr].bounds.intersect(cellRegion.bounds).getArea();
				if (maxOverlap < overlap) {
					maxOverlap = overlap;
					maxOverlapColumnRegion = this.columnRegions[cr];
				}
			}
			return maxOverlapColumnRegion;
		}
		
		/**
		 * Retrieve the width of the column, also accounting for large column
		 * gaps spanned by multi-column cells. This is intended to simplify
		 * displaying the table in a UI.
		 * @return the width of the column
		 */
		public int getWidth() {
			if (this.width == -1) {
				int width = -1;
				for (int r = 0; r < this.table.rows.length; r++) {
					/* need to go via cells to also cover potentially excessive
					 * column gaps in tables with multi-column cells) */
					ImTableCell itc = this.table.cells[r][this.c];
					if (itc.cellRegion != null)
						width = Math.max(width, ((itc.cellRegion.bounds.getWidth() + (itc.colSpan / 2)) / itc.colSpan));
				}
				this.width = width;
			}
			return this.width;
		}
		private int width = -1;
	}
	
	/**
	 * A row in an Image Markup table wrapper.
	 * 
	 * @author sautter
	 */
	public static class ImTableRow {
		
		/** the underlying table row regions (one for each horizontally connected table region) */
		public final ImRegion[] rowRegions;
		
		ImTable table;
		boolean isColumnHeaderRow;
		int r;
		
		ImTableRow(ImRegion[] rowRegions, int r) {
			this.rowRegions = rowRegions;
			this.r = r;
		}
		
		/**
		 * Retrieve the table the row belongs to.
		 * @return the parent table
		 */
		public ImTable getTable() {
			return this.table;
		}
		
		/**
		 * Retrieve the vertical position of the row down the table,
		 * i.e., its row index.
		 * @return the vertical position
		 */
		public int getPosition() {
			return this.r;
		}
		
		/**
		 * Test whether or not the row contains column header cells.
		 * @return true if the row contains column headers
		 */
		public boolean isColumnHeaderRow() {
			return this.isColumnHeaderRow;
		}
		
		/**
		 * Retrieve the cell at position <code>c</code> across the row.
		 * @param c the column index to access
		 * @return the cell at the specified position
		 */
		public ImTableCell getCellAt(int c) {
			return this.table.cells[this.r][c]; // cells are in rows
		}
		
		/**
		 * Retrieve the table row region that contains the table cell at
		 * position <code>c</code>.
		 * @param c the column index of the cell
		 * @return the table row region at the specified position
		 */
		public ImRegion getRegionAt(int c) {
			return this.getRegionFor(this.getCellAt(c).cellRegion);
		}
		ImRegion getRegionFor(ImRegion cellRegion) {
			int maxOverlap = 0;
			ImRegion maxOverlapRowRegion = null;
			for (int rr = 0; rr < this.rowRegions.length; rr++) {
				if (this.rowRegions[rr].pageId != cellRegion.pageId)
					continue;
				if (this.rowRegions[rr].bounds.includes(cellRegion.bounds, false))
					return this.rowRegions[rr];
				if (!this.rowRegions[rr].bounds.overlaps(cellRegion.bounds))
					continue;
				int overlap = this.rowRegions[rr].bounds.intersect(cellRegion.bounds).getArea();
				if (maxOverlap < overlap) {
					maxOverlap = overlap;
					maxOverlapRowRegion = this.rowRegions[rr];
				}
			}
			return maxOverlapRowRegion;
		}
		
		/**
		 * Retrieve the height of the row, also accounting for large row gaps
		 * spanned by multi-row cells. This is intended to simplify displaying
		 * the table in a UI.
		 * @return the height of the row
		 */
		public int getHeight() {
			if (this.height == -1) {
				int height = -1;
				for (int c = 0; c < this.table.cols.length; c++) {
					/* need to go via cells to also cover potentially excessive
					 * row gaps in tables with multi-row cells) */
					ImTableCell itc = this.table.cells[this.r][c];
					if (itc.cellRegion != null)
						height = Math.max(height, ((itc.cellRegion.bounds.getHeight() + (itc.rowSpan / 2)) / itc.rowSpan));
				}
				this.height = height;
			}
			return this.height;
		}
		private int height = -1;
	}
	
	/**
	 * A cell in an Image Markup table wrapper.
	 * 
	 * @author sautter
	 */
	public static class ImTableCell {
		
		/** the underlying table cell region (can span multiple columns or rows) */
		public final ImRegion cellRegion;
		
		/** if the cell a header cell, i.e., a column header or row label? */
		public final boolean isHeader;
		
		/** the words contained in the table cell */
		public final ImWord[] words;
		
		ImTable table;
		int c;
		int r;
		int colSpan = 1;
		int rowSpan = 1;
		
		ImTableCell(ImPage page, ImRegion cellRegion, boolean isHeader, int c, int r) {
			this.cellRegion = cellRegion;
			this.isHeader = isHeader;
			this.words = page.getWordsInside(this.cellRegion.bounds);
			if (this.words.length > 1)
				Arrays.sort(this.words, textStreamOrder);
			this.c = c;
			this.r = r;
		}
		
		/**
		 * Retrieve the table the cell belongs to.
		 * @return the parent table
		 */
		public ImTable getTable() {
			return this.table;
		}
		
		/**
		 * Retrieve the column index of the cell, i.e., its horizontal position
		 * across the table.
		 * @return the column index
		 */
		public int getColumnIndex() {
			return this.c;
		}
		
		/**
		 * Retrieve the number of columns spanned by the cell.
		 * @return the number of columns
		 */
		public int getColumnExtent() {
			return this.colSpan;
		}
		
		/**
		 * Retrieve the <code>c</code>-th column spanned by the cell. The
		 * argument index is relative to the leftmost column spanned by the
		 * cell.
		 * @param c the column index to access
		 * @return the column at the specified index
		 */
		public ImTableColumn getColumnAt(int c) {
			return this.table.cols[this.c + c];
		}
		
		/**
		 * Retrieve the table column region that overlaps the table cell at
		 * position <code>c</code>, <code>r</code> relative to the cell proper.
		 * @param c the column index within the cell
		 * @param r the row index withing the cell
		 * @return the table column region at the specified position withing
		 *        the cell
		 */
		public ImRegion getColumnRegionAt(int c, int r) {
			return this.getColumnAt(c).getRegionFor(this.cellRegion);
		}
		
		/**
		 * Retrieve the row index of the cell, i.e., its vertical position down
		 * the table.
		 * @return the row index
		 */
		public int getRowIndex() {
			return this.r;
		}
		
		/**
		 * Retrieve the number of rows spanned by the cell.
		 * @return the number of rows
		 */
		public int getRowExtent() {
			return this.rowSpan;
		}
		
		/**
		 * Retrieve the <code>r</code>-th row spanned by the cell. The argument
		 * index is relative to the topmost row spanned by the cell.
		 * @param r the row index to access
		 * @return the row at the specified index
		 */
		public ImTableRow getRowAt(int r) {
			return this.table.rows[this.r + r];
		}
		
		/**
		 * Retrieve the table row region that overlaps the table cell at
		 * position <code>c</code>, <code>r</code> relative to the cell proper.
		 * @param c the column index withing the cell
		 * @param r the row index within the cell
		 * @return the table row region at the specified position withing the
		 *        cell
		 */
		public ImRegion getRowRegionAt(int c, int r) {
			return this.getRowAt(r).getRegionFor(this.cellRegion);
		}
	}
//	
//	private static class TableCell {
//		final ImRegion cell;
//		final boolean isHeader;
//		final ImWord[] words;
//		int colSpan = 1;
//		int rowSpan = 1;
//		TableCell(ImPage page, ImRegion cell, boolean isHeader) {
//			this.cell = cell;
//			this.isHeader = isHeader;
//			this.words = page.getWordsInside(this.cell.bounds);
//			if (this.words.length > 1)
//				Arrays.sort(this.words, textStreamOrder);
//		}
//		String getString() {
//			String cellStr = getString(this.words[0], this.words[this.words.length-1], true);
//			if (cellStr.matches("\\.\\s*[0-9]+"))
//				return ("0." + cellStr.substring(".".length()).trim());
//			else return cellStr;
//		}
//	}
	
	//	FOR TEST PURPOSES ONLY !!!
	public static void main(String[] args) throws Exception {
		ImDocument doc = ImDocumentIO.loadDocument(new File("E:/Testdaten/PdfExtract/zt00872.pdf.tables.imdir"));
		ImPage page = doc.getPage(8);
		ImRegion[] tables = page.getRegions(ImRegion.TABLE_TYPE);
		Arrays.sort(tables, topDownOrder);
		System.out.println("Get + println");
		for (int t = 0; t < tables.length; t++) {
			System.out.println(getTableData(tables[t], ';'));
			System.out.println(getTableData(tables[t], '\t'));
		}
		System.out.println("Write");
		for (int t = 0; t < tables.length; t++)
			writeTableData(tables[t], 'X', new PrintWriter(System.out, true));
		System.out.println();
		System.out.println("Get + println");
		for (int t = 0; t < tables.length; t++) {
			if (tables[t].hasAttribute("rowsContinueFrom"))
				continue;
			if (tables[t].hasAttribute("colsContinueFrom"))
				continue;
			System.out.println(getTableGridData(tables[t], ','));
			System.out.println(getTableGridData(tables[t], '\t'));
		}
		System.out.println("Write");
		for (int t = 0; t < tables.length; t++) {
			if (tables[t].hasAttribute("rowsContinueFrom"))
				continue;
			if (tables[t].hasAttribute("colsContinueFrom"))
				continue;
			writeTableGridData(tables[t], 'X', new PrintWriter(System.out, true));
		}
	}
	
	/* This will simply repeat content of multi-column or multi-row cells, but
	 * there is little else we can do for plain text formats */
//	private static void writeCellData(TableCell cell, char separator, boolean includeMarkup, boolean includeIDs, Writer out) throws IOException {
//		if (cell.words.length == 0) {
//			if ((separator == ',') || (separator == ';'))
//				out.write("\"\"");
//			else if (separator == 'X')
//				out.write("<" + (cell.isHeader ? "th" : "td") + ((cell.colSpan == 1) ? "" : (" colspan=\"" + cell.colSpan + "\"")) + ((cell.rowSpan == 1) ? "" : (" rowspan=\"" + cell.rowSpan + "\"")) + (includeIDs ? (" id=\"" + cell.cell.getUUID() + "\"") : "") + "/>");
//		}
//		else if (includeMarkup) {
//			ImDocumentRoot cellImDoc = new ImDocumentRoot(cell.cell, (ImDocumentRoot.NORMALIZATION_LEVEL_STREAMS));
//			DocumentRoot cellDoc = Gamta.copyDocument(cellImDoc);
//			cellDoc.setAnnotationNestingOrder("table thead tbody tfoot tr th td " + cellDoc.getAnnotationNestingOrder());
//			MutableAnnotation cellAnnot = cellDoc.addAnnotation((cell.isHeader ? "th" : "td"), 0, cellDoc.size());
//			if (cell.colSpan != 1)
//				cellAnnot.setAttribute("colspan", ("" + cell.colSpan));
//			if (cell.rowSpan != 1)
//				cellAnnot.setAttribute("rowspan", ("" + cell.rowSpan));
//			if (includeIDs)
//				cellAnnot.setAttribute(Annotation.ANNOTATION_ID_ATTRIBUTE, cell.cell.getUUID());
//			AnnotationFilter.removeAnnotations(cellDoc, MutableAnnotation.PARAGRAPH_TYPE);
//			if (cell.isHeader) {
//				AnnotationFilter.renameAnnotations(cellDoc, "td", "th");
//				AnnotationFilter.removeDuplicates(cellDoc, "th");
//			}
//			else AnnotationFilter.removeDuplicates(cellDoc, "td");
//			AnnotationUtils.writeXML(cellAnnot, out, includeIDs);
//		}
//		else if (separator == 'X') {
//			out.write("<" + (cell.isHeader ? "th" : "td") + ((cell.colSpan == 1) ? "" : (" colspan=\"" + cell.colSpan + "\"")) + ((cell.rowSpan == 1) ? "" : (" rowspan=\"" + cell.rowSpan + "\"")) + (includeIDs ? (" id=\"" + cell.cell.getUUID() + "\"") : "") + ">");
//			out.write(AnnotationUtils.escapeForXml(cell.getString()));
//			out.write("</" + (cell.isHeader ? "th" : "td") + ">\r\n");
//		}
//		else if ((separator == ',') || (separator == ';')) {
//			out.write('"');
//			String cellStr = cell.getString();
//			for (int i = 0; i < cellStr.length(); i++) {
//				char ch = cellStr.charAt(i);
//				if (ch == '"')
//					out.write('"');
//				out.write(ch);
//			}
//			out.write('"');
//		}
//		else out.write(cell.getString());
//	}
	private static void writeCellData(ImTableCell imtc, char separator, boolean includeMarkup, boolean includeIDs, Writer out) throws IOException {
		if (imtc.words.length == 0) {
			if ((separator == ',') || (separator == ';'))
				out.write("\"\"");
			else if (separator == 'X')
				out.write("<" + (imtc.isHeader ? "th" : "td") + ((imtc.colSpan == 1) ? "" : (" colspan=\"" + imtc.colSpan + "\"")) + ((imtc.rowSpan == 1) ? "" : (" rowspan=\"" + imtc.rowSpan + "\"")) + (includeIDs ? (" id=\"" + imtc.cellRegion.getUUID() + "\"") : "") + "/>");
		}
		else if (includeMarkup) {
			ImDocumentRoot cellImDoc = new ImDocumentRoot(imtc.cellRegion, (ImDocumentRoot.NORMALIZATION_LEVEL_STREAMS));
			DocumentRoot cellDoc = Gamta.copyDocument(cellImDoc);
			cellDoc.setAnnotationNestingOrder("table thead tbody tfoot tr th td " + cellDoc.getAnnotationNestingOrder());
			MutableAnnotation cellAnnot = cellDoc.addAnnotation((imtc.isHeader ? "th" : "td"), 0, cellDoc.size());
			if (imtc.colSpan != 1)
				cellAnnot.setAttribute("colspan", ("" + imtc.colSpan));
			if (imtc.rowSpan != 1)
				cellAnnot.setAttribute("rowspan", ("" + imtc.rowSpan));
			if (includeIDs)
				cellAnnot.setAttribute(Annotation.ANNOTATION_ID_ATTRIBUTE, imtc.cellRegion.getUUID());
			AnnotationFilter.removeAnnotations(cellDoc, MutableAnnotation.PARAGRAPH_TYPE);
			if (imtc.isHeader) {
				AnnotationFilter.renameAnnotations(cellDoc, "td", "th");
				AnnotationFilter.removeDuplicates(cellDoc, "th");
			}
			else AnnotationFilter.removeDuplicates(cellDoc, "td");
			AnnotationUtils.writeXML(cellAnnot, out, includeIDs);
		}
		else if (separator == 'X') {
			out.write("<" + (imtc.isHeader ? "th" : "td") + ((imtc.colSpan == 1) ? "" : (" colspan=\"" + imtc.colSpan + "\"")) + ((imtc.rowSpan == 1) ? "" : (" rowspan=\"" + imtc.rowSpan + "\"")) + (includeIDs ? (" id=\"" + imtc.cellRegion.getUUID() + "\"") : "") + ">");
			out.write(AnnotationUtils.escapeForXml(getCellString(imtc)));
			out.write("</" + (imtc.isHeader ? "th" : "td") + ">");
			writeNewline(out);
		}
		else if ((separator == ',') || (separator == ';')) {
			out.write('"');
			String cellStr = getCellString(imtc);
			for (int i = 0; i < cellStr.length(); i++) {
				char ch = cellStr.charAt(i);
				if (ch == '"')
					out.write('"');
				out.write(ch);
			}
			out.write('"');
		}
		else out.write(getCellString(imtc));
	}
	private static String getCellString(ImTableCell imtc) {
		String cellStr = getString(imtc.words[0], imtc.words[imtc.words.length-1], true);
		if (cellStr.matches("\\.\\s*[0-9]+"))
			return ("0." + cellStr.substring(".".length()).trim());
		else return cellStr;
	}
}
