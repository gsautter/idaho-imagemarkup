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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
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
import java.util.Set;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImRegion;
import de.uka.ipd.idaho.im.ImWord;

/**
 * Utility library for modifying Image Markup documents.
 * 
 * @author sautter
 */
public class ImUtils implements ImagingConstants {
	
	/** Comparator sorting ImWords in layout order, i.e., top to bottom and
	 * left to right. With arrays or collections whose content objects are NOT
	 * ImWords, using this comparator results in ClassCastExceptions.<br>
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
	
	/** Comparator sorting ImWords in text stream order. ImWords belonging to
	 * different logical text streams are compared based on their text stream
	 * ID. With arrays or collections whose content objects are NOT ImWords,
	 * using this comparator results in ClassCastExceptions. */
	public static Comparator textStreamOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord imw1 = ((ImWord) obj1);
			ImWord imw2 = ((ImWord) obj2);
			
			//	quick check
			if (imw1 == imw2)
				return 0;
			
			//	same text stream, compare page ID and position
			if (imw1.getTextStreamId().equals(imw2.getTextStreamId()))
				return ((imw1.pageId == imw2.pageId) ? (imw1.getTextStreamPos() - imw2.getTextStreamPos()) : (imw1.pageId - imw2.pageId));
			
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
	
	/** Comparator sorting ImLayoutObjects left to right with regard to their
	 * center points. With arrays or collections whose content objects are NOT
	 * ImLayoutObjects, using this comparator results in ClassCastExceptions. */
	public static final Comparator leftRightOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
			return ((ilo1.bounds.left + ilo1.bounds.right) - (ilo2.bounds.left + ilo2.bounds.right));
		}
	};
	
	/** Comparator sorting ImLayoutObjects top to bottom with regard to their
	 * center points. With arrays or collections whose content objects are NOT
	 * ImLayoutObjects, using this comparator results in ClassCastExceptions. */
	public static final Comparator topDownOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
			return ((ilo1.bounds.top + ilo1.bounds.bottom) - (ilo2.bounds.top + ilo2.bounds.bottom));
		}
	};
	
	
	/** Comparator sorting ImLayoutObjects in descending order by the area of
	 * their bounding boxes. With arrays or collections whose content objects
	 * are NOT ImLayoutObjects, using this comparator results in
	 * ClassCastExceptions. */
	public static final Comparator sizeOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImLayoutObject ilo1 = ((ImLayoutObject) obj1);
			ImLayoutObject ilo2 = ((ImLayoutObject) obj2);
			return (this.getSize(ilo2.bounds) - this.getSize(ilo1.bounds));
		}
		private final int getSize(BoundingBox bb) {
			return ((bb.right - bb.left) * (bb.bottom - bb.top));
		}
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
		ssp.addSelector(text, existingTypes, existingType, allowInput);
		if (ssp.prompt(DialogFactory.getTopWindow()) != JOptionPane.OK_OPTION)
			return null;
		return ssp.typeOrNameAt(0, true);
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
		ssp.addSelector(textOld, existingTypes, existingType, false);
		ssp.addSelector(textNew, existingTypes, existingType, true);
		if (ssp.prompt(DialogFactory.getTopWindow()) != JOptionPane.OK_OPTION)
			return null;
		String typeOld = ssp.typeOrNameAt(0, false);
		String typeNew = ssp.typeOrNameAt(1, true);
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
	 * @param wordOrder the word order to apply
	 */
	public static void orderStream(ImWord[] words, Comparator wordOrder) {
		
		//	anything to work with?
		if (words.length < 2)
			return;
		
		//	order words (use dedicated sort method for left-right-top-down)
		if (wordOrder == leftRightTopDownOrder)
			sortLeftRightTopDown(words);
		else if (wordOrder == bottomUpLeftRightOrder)
			sortBottomUpLeftRight(words);
		else if (wordOrder == topDownRightLeftOrder)
			sortTopDownRightLeft(words);
		else Arrays.sort(words, wordOrder);
		
		//	index words from array
		HashSet wordIDs = new HashSet();
		for (int w = 0; w < words.length; w++)
			wordIDs.add(words[w].getLocalID());
		
		//	find predecessor and successor
		ArrayList predecessors = new ArrayList();
		ArrayList successors = new ArrayList();
		for (int w = 0; w < words.length; w++) {
			ImWord prev = words[w].getPreviousWord();
			if ((prev != null) && !wordIDs.contains(prev.getLocalID())) {
//				System.out.println("External predecessor '" + prev.getString() + "' (page " + prev.pageId + " at " + prev.bounds + ") from '" + words[w].getString() + "' (page " + words[w].pageId + " at " + words[w].bounds + ")");
				predecessors.add(prev);
			}
			ImWord next = words[w].getNextWord();
			if ((next != null) && !wordIDs.contains(next.getLocalID())) {
//				System.out.println("External successor '" + next.getString() + "' (page " + next.pageId + " at " + next.bounds + ") from '" + words[w].getString() + "' (page " + words[w].pageId + " at " + words[w].bounds + ")");
				successors.add(next);
			}
		}
		
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
		
		//	connect first and last word
		Collections.sort(predecessors, Collections.reverseOrder(wordOrder));
		if (predecessors.size() != 0) {
			ImWord prev = (ImWord) predecessors.get(0);
			if (words[0].getPreviousWord() != prev) {
//				System.out.println("Setting external predecessor '" + prev.getString() + "' (page " + prev.pageId + " at " + prev.bounds + ") for '" + words[0].getString() + "' (page " + words[0].pageId + " at " + words[0].bounds + ")");
				words[0].setPreviousWord(prev);
			}
		}
		Collections.sort(successors, wordOrder);
		if (successors.size() != 0) {
			ImWord next = ((ImWord) successors.get(0));
			if (words[words.length-1].getNextWord() != next) {
//				System.out.println("Setting external successor '" + next.getString() + "' (page " + next.pageId + " at " + next.bounds + ") for '" + words[words.length-1].getString() + "' (page " + words[words.length-1].pageId + " at " + words[words.length-1].bounds + ")");
				words[words.length-1].setNextWord(next);
			}
		}
	}
	
	/**
	 * Make a series of words a separate logical text stream. The words in the
	 * argument array need not belong to an individual logical text stream, nor
	 * need they be single chunks of the logical text streams involved. If the
	 * <code>sType</code> argument is non-null, the type of the newly created
	 * logical text stream is set to this type. If the <code>aType</code>
	 * argument is non-null, an annotation with that type is added to mark the
	 * newly created logical text stream. 
	 * @param words the words to make a text stream
	 * @param sType the text stream type
	 * @param aType the type annotation to annotate the text stream with
	 * @return the annotation marking the newly created text stream, if any
	 */
	public static ImAnnotation makeStream(ImWord[] words, String sType, String aType) {
		
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
			textStreamParts.addLast(cutOutTextStream(tspStart, tspEnd));
			
			//	start new text stream chunk
			tspStart = words[w];
			tspEnd = words[w];
		}
		
		//	cut out and store last chunk
		textStreamParts.addLast(cutOutTextStream(tspStart, tspEnd));
		
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
	 * successor of <code>last</code>.
	 * @param first the first word of the chunk
	 * @param last the last word of the chunk
	 * @return the head of the newly created text stream
	 */
	public static ImWord cutOutTextStream(ImWord first, ImWord last) {
		
		//	check arguments
		if (!first.getTextStreamId().equals(last.getTextStreamId()))
			return null;
		
		//	swap words if in wrong order
		if (0 < textStreamOrder.compare(first, last)) {
			ImWord temp = first;
			first = last;
			last = temp;
		}
		
		//	cut out text stream
		ImWord oldFirstPrev = first.getPreviousWord();
		ImWord oldLastNext = last.getNextWord();
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
		StringBuffer sb = new StringBuffer();
		for (ImWord imw = firstWord; imw != null; imw = imw.getNextWord()) {
			sb.append(imw.getString());
			if (imw == lastWord)
				break;
			if ((imw.getNextRelation() == ImWord.NEXT_RELATION_SEPARATE) && (imw.getNextWord() != null) && isSpace(imw, imw.getNextWord()) && Gamta.insertSpace(imw.getString(), imw.getNextWord().getString()))
				sb.append(" ");
			else if (imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
				sb.append(ignoreLineBreaks ? " " : "\r\n");
			else if ((imw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) && (sb.length() != 0))
				sb.deleteCharAt(sb.length()-1);
		}
		return sb.toString();
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
	 * @return the concatenated text;
	 */
	public static String getString(ImWord[] words, boolean ignoreLineBreaks) {
		return getString(words, null, ignoreLineBreaks);
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
			if ((w+1) < words.length) {
				if (words[w].getNextWord() != words[w+1])
					sb.append(" ");
				else if ((words[w].getNextRelation() == ImWord.NEXT_RELATION_SEPARATE) && (words[w].getNextWord() != null) && isSpace(words[w], words[w].getNextWord()) && Gamta.insertSpace(words[w].getString(), words[w].getNextWord().getString()))
					sb.append(" ");
				else if (words[w].getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
					sb.append(ignoreLineBreaks ? " " : "\r\n");
				else if ((words[w].getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) && (sb.length() != 0))
					sb.deleteCharAt(sb.length()-1);
			}
		}
		return sb.toString();
	}
	
	private static boolean isSpace(ImWord word1, ImWord word2) {
		if (word1.bounds.bottom < word2.centerY)
			return true; // line offset
		if (word2.bounds.bottom < word1.centerY)
			return true; // line offset
		if (word2.centerY < word1.bounds.top)
			return true; // line offset
		if (word1.centerY < word2.bounds.top)
			return true; // line offset
		if (word2.bounds.left < word1.centerX)
			return true; // second word to the left ... WTF
		int wordDist = (word2.bounds.left - word1.bounds.right);
		int wordHeight = ((word1.bounds.getHeight() + word2.bounds.getHeight()) / 2);
//		return (wordHeight <= (wordDist * 5)); // should be OK as an estimate, and with some safety margin, at least for born-digital text (0.25 is smallest defaulting space width)
		return ((wordHeight * 3) <= (wordDist * 20)); // 15% should be OK as an estimate lower bound, and with some safety margin, at least for born-digital text (0.25 is smallest defaulting space width)
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
	 * @param inside search captions inside the target region?
	 * @param matchTargetType match caption type and target type?
	 * @return an array holding potential captions for the argument target region
	 * @see de.uka.ipd.idaho.im.util.ImUtils#isCaptionTargetMatch(BoundingBox, BoundingBox, int))
	 */
	public static ImAnnotation[] findCaptions(ImRegion target, boolean above, boolean below, boolean beside, boolean inside, boolean matchTargetType) {
		
		//	quick check alignment switches
		if (!above && !below)
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
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()))
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
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()))
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
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()))
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
					if (ImAnnotation.CAPTION_TYPE.equals(captionAnnots[a].getType()))
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
		ImRegion[] tableRows = getRegionsInside(page, table.bounds, ImRegion.TABLE_ROW_TYPE, false);
		
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
		ImRegion[] tableCols = getRegionsInside(page, table.bounds, ImRegion.TABLE_COL_TYPE, false);
		
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
	 * @return an array holding the table cells
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
	 * @return an array holding the table cells
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
	 * @return an array holding the table cells
	 */
	public static ImRegion[][] getTableCells(ImRegion table, Set ignoreWords, ImRegion[] rows, ImRegion[] cols, boolean cleanUpSpurious) {
		
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
		Arrays.sort(rows, topDownOrder);
		Arrays.sort(cols, leftRightOrder);
		
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
				
				//	create cell bounds
				BoundingBox cellBounds = new BoundingBox(cols[c].bounds.left, cols[c].bounds.right, rows[r].bounds.top, rows[r].bounds.bottom);
				
				//	get cell words
				ImWord[] cellWords = page.getWordsInside(cellBounds);
				if (ignoreWords != null)
					cellWords = filterWords(cellWords, ignoreWords);
//				
//				//	shrink cell bounds to words
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
				for (int w = 0; w < cellWords.length; w++) {
					String wordDirection = ((String) (cellWords[w].getAttribute(ImWord.TEXT_DIRECTION_ATTRIBUTE, ImWord.TEXT_DIRECTION_LEFT_RIGHT)));
					if (ImWord.TEXT_DIRECTION_BOTTOM_UP.equals(wordDirection))
						buWords++;
					else if (ImWord.TEXT_DIRECTION_TOP_DOWN.equals(wordDirection))
						tdWords++;
					else lrWords++;
				}
				
				//	cut out cell words to avoid order mix-up errors
				makeStream(cellWords, null, null);
				
				//	order cell words dependent on direction
				if ((lrWords < buWords) && (tdWords < buWords))
					orderStream(cellWords, bottomUpLeftRightOrder);
				else if ((lrWords < tdWords) && (buWords < tdWords))
					orderStream(cellWords, topDownRightLeftOrder);
				else orderStream(cellWords, leftRightTopDownOrder);
				
				//	attach cell word stream
				Arrays.sort(cellWords, textStreamOrder);
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
//		return areTableRowsCompatible(table1, table2, false);
		return areTableRowsCompatible(table1, table2, true);
	}
//	
//	/**
//	 * Test if the rows of two tables are compatible. In particular, this means
//	 * that (a) the tables have the same number of rows, and (b) the rows have
//	 * the same leading labels. In addition, both tables have to be attached to
//	 * their pages.
//	 * @param table1 the first table
//	 * @param table2 the second table
//	 * @param fuzzyLabels allow some degree of mismatch in row labels?
//	 * @return true if the rows of the argument tables are compatible
//	 */
//	public static boolean areTableRowsCompatible(ImRegion table1, ImRegion table2, boolean fuzzyLabels) {
//		if ((table1.getPage() == null) || (table2.getPage() == null))
//			return false;
//		
//		//	get cells
//		ImRegion[][] cells1 = getTableCells(table1, null, null);
//		if (cells1 == null)
//			return false;
//		ImRegion[][] cells2 = getTableCells(table2, null, null);
//		if (cells2 == null)
//			return false;
//		
//		//	do we have the same number of rows?
//		if (cells1.length != cells2.length)
//			return false;
//		
//		//	compare row labels (safe for header row)
//		int labelMatchCount = 0;
//		int labelMismatchCount = 0;
//		for (int r = 1; r < cells1.length; r++) {
//			ImWord[] words1 = cells1[r][0].getWords();
//			ImWord[] words2 = cells2[r][0].getWords();
//			if (words1.length != words2.length) {
//				if (fuzzyLabels) {
//					labelMismatchCount++;
//					continue;
//				}
//				else return false;
//			}
//			if (words1.length == 0)
//				continue;
//			Arrays.sort(words1, textStreamOrder);
//			String label1 = getString(words1[0], words1[words1.length-1], true);
//			Arrays.sort(words2, textStreamOrder);
//			String label2 = getString(words2[0], words2[words2.length-1], true);
//			if (label1.equals(label2))
//				labelMatchCount++;
//			else if (fuzzyLabels)
//				labelMismatchCount++;
//			else return false;
//		}
//		
//		//	(majority of) row labels match
//		return ((labelMatchCount == 0) ? (labelMismatchCount == 0) : (labelMatchCount > labelMismatchCount));
//	}
	
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
		String[] labels1 = ImUtils.getTableRowLabels(cells1);
		String[] labels2 = ImUtils.getTableRowLabels(cells2);
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
		return getTableRowLabels(cells);
	}
	
	private static String[] getTableRowLabels(ImRegion[][] cells) {
		String[] rowLabels = new String[cells.length];
		for (int r = 0; r < cells.length; r++) {
			if ((r != 0) && (cells[r][0] == cells[r-1][0])) {
				rowLabels[r] = rowLabels[r-1];
				continue;
			}
			ImWord[] words = cells[r][0].getWords();
			if (words.length == 0)
				rowLabels[r] = "";
			else {
				Arrays.sort(words, textStreamOrder);
				rowLabels[r] = getString(words[0], words[words.length-1], true);
			}
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
//		return areTableColumnsCompatible(table1, table2, false);
		return areTableColumnsCompatible(table1, table2, true);
	}
//	
//	/**
//	 * Test if the columns of two tables are compatible. In particular, this
//	 * means that (a) the tables have the same number of columns, and (b) the
//	 * columns have the same headers. In addition, both tables have to be
//	 * attached to their pages. If <code>fuzzyHeaders</code> is
//	 * <code>true</code>, the majority of the column headers has to match
//	 * instead of all of them.
//	 * @param table1 the first table
//	 * @param table2 the second table
//	 * @param fuzzyHeaders allow some degree of mismatch in column headers?
//	 * @return true if the columns of the argument tables are compatible
//	 */
//	public static boolean areTableColumnsCompatible(ImRegion table1, ImRegion table2, boolean fuzzyHeaders) {
//		if ((table1.getPage() == null) || (table2.getPage() == null))
//			return false;
//		
//		//	get cells
//		ImRegion[][] cells1 = getTableCells(table1, null, null);
//		if (cells1 == null)
//			return false;
//		ImRegion[][] cells2 = getTableCells(table2, null, null);
//		if (cells2 == null)
//			return false;
//		
//		//	do we have the same number of columns
//		if (cells1[0].length != cells2[0].length)
//			return false;
//		
//		//	compare column headers (safe for label column)
//		int headerMatchCount = 0;
//		int headerMismatchCount = 0;
//		for (int c = 1; c < cells1[0].length; c++) {
//			ImWord[] words1 = cells1[0][c].getWords();
//			ImWord[] words2 = cells2[0][c].getWords();
//			if (words1.length != words2.length) {
//				if (fuzzyHeaders) {
//					headerMismatchCount++;
//					continue;
//				}
//				else return false;
//			}
//			if (words1.length == 0)
//				continue;
//			Arrays.sort(words1, textStreamOrder);
//			String header1 = getString(words1[0], words1[words1.length-1], true);
//			Arrays.sort(words2, textStreamOrder);
//			String header2 = getString(words2[0], words2[words2.length-1], true);
//			if (header1.equals(header2))
//				headerMatchCount++;
//			else if (fuzzyHeaders)
//				headerMismatchCount++;
//			else return false;
//		}
//		
//		//	(majority of) column headers match
//		return ((headerMatchCount == 0) ? (headerMismatchCount == 0) : (headerMatchCount > headerMismatchCount));
//	}
	
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
		String[] labels1 = ImUtils.getTableColumnHeaders(cells1);
		String[] labels2 = ImUtils.getTableColumnHeaders(cells2);
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
		return getTableColumnHeaders(cells);
	}
	
	private static String[] getTableColumnHeaders(ImRegion[][] cells) {
		String[] colHeaders = new String[cells[0].length];
		for (int c = 0; c < cells[0].length; c++) {
			if ((c != 0) && (cells[0][c] == cells[0][c-1])) {
				colHeaders[c] = colHeaders[c-1];
				continue;
			}
			ImWord[] words = cells[0][c].getWords();
			if (words.length == 0)
				colHeaders[c] = "";
			else {
				Arrays.sort(words, textStreamOrder);
				colHeaders[c] = getString(words[0], words[words.length-1], true);
			}
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
		if (id == null)
			return null;
		if (!id.matches("[0-9]+\\.\\[[0-9]+\\,[0-9]+\\,[0-9]+\\,[0-9]+\\]"))
			return null;
		ImPage page = doc.getPage(Integer.parseInt(id.substring(0, id.indexOf('.'))));
		if (page == null)
			return null;
		ImRegion[] pageTables = page.getRegions(ImRegion.TABLE_TYPE);
		for (int t = 0; t < pageTables.length; t++) {
			if (id.endsWith("." + pageTables[t].bounds))
				return pageTables[t];
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
	 * Copy the data from a table in some column based format like CSV. If the
	 * argument separator is the comma or semicolon, field values are enclosed
	 * in double quotes.
	 * @param table the table whose data to copy
	 * @param separator the separator to use
	 * @return the data from the argument table
	 */
	public static String getTableData(ImRegion table, char separator) {
		return getTableDataString(table, separator);
	}
	
	/**
	 * Copy the data from a table in XML, specifically in XHTML.
	 * @param table the table whose data to copy
	 * @return the data from the argument table
	 */
	public static String getTableDataXml(ImRegion table) {
		return getTableDataString(table, 'X');
	}
	
	private static String getTableDataString(ImRegion table, char separator) {
		
		//	get rows and columns
		ImRegion[] rows = getRegionsInside(table.getPage(), table.bounds, ImRegion.TABLE_ROW_TYPE, false);
		Arrays.sort(rows, topDownOrder);
		ImRegion[] cols = getRegionsInside(table.getPage(), table.bounds, ImRegion.TABLE_COL_TYPE, false);
		Arrays.sort(cols, leftRightOrder);
		
		//	get cells and backing page
		ImRegion[][] cells = getTableCells(table, null, rows, cols, false);
		ImPage page = table.getPage();
		
		//	write table data
		StringBuffer tableData = new StringBuffer();
		if (separator == 'X')
			tableData.append("<table>\r\n");
		for (int r = 0; r < rows.length; r++) {
			if (separator == 'X')
				tableData.append("<tr>\r\n");
			for (int c = 0; c < cols.length; c++) {
				int colSpan = 1;
				int rowSpan = 1;
				if (separator == 'X') {
					if ((c != 0) && (cells[r][c] == cells[r][c-1]))
						continue;
					if ((r != 0) && (cells[r][c] == cells[r-1][c]))
						continue;
					for (int lc = (c+1); lc < cols.length; lc++) {
						if (cells[r][lc] == cells[r][c])
							colSpan++;
						else break;
					}
					for (int lr = (r+1); lr < rows.length; lr++) {
						if (cells[lr][c] == cells[r][c])
							rowSpan++;
						else break;
					}
					appendCellData(page, cells[r][c], separator, colSpan, rowSpan, tableData);
				}
				else {
					appendCellData(page, cells[r][c], separator, colSpan, rowSpan, tableData);
					if ((c+1) == cols.length)
						tableData.append("\r\n");
					else tableData.append(separator);
				}
			}
			if (separator == 'X')
				tableData.append("</tr>\r\n");
		}
		if (separator == 'X')
			tableData.append("</table>\r\n");
		
		//	finally ...
		return tableData.toString();
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
		return getTableGridDataString(table, separator);
	}
	
	/**
	 * Copy the data from a whole grid of connected tables in XML, specifically
	 * in XHTML.
	 * @param table the table whose data to copy
	 * @return the data from the argument table
	 */
	public static String getTableGridDataXml(ImRegion table) {
		return getTableGridDataString(table, 'X');
	}
	
	private static String getTableGridDataString(ImRegion table, char separator) {
		
		//	collect data row-wise
		LinkedList rowDataList = new LinkedList();
		
		//	get whole grid of connected tables
		ImRegion[][] tables = getConnectedTables(table);
		for (int ty = 0; ty < tables.length; ty++) {
			StringBuffer[] rowData = null;
			for (int tx = 0; tx < tables[ty].length; tx++) {
				
				//	get backing page
				ImPage page = tables[ty][tx].getPage();
				
				//	get columns and rows of current table
				ImRegion[] rows = getRegionsInside(page, tables[ty][tx].bounds, ImRegion.TABLE_ROW_TYPE, false);
				Arrays.sort(rows, topDownOrder);
				ImRegion[] cols = getRegionsInside(page, tables[ty][tx].bounds, ImRegion.TABLE_COL_TYPE, false);
				Arrays.sort(cols, leftRightOrder);
				
				//	initialize data on first table in grid row 
				if (rowData == null) {
					rowData = new StringBuffer[rows.length];
					for (int r = 0; r < rowData.length; r++)
						rowData[r] = new StringBuffer();
				}
				
				//	get cells
				ImRegion[][] cells = getTableCells(tables[ty][tx], null, rows, cols, false);
				
				//	append data to rows (column header row only for top row of grid, row label column only for first column of grid)
				for (int r = ((ty == 0) ? 0 : 1); r < Math.min(rows.length, rowData.length); r++)
					for (int c = ((tx == 0) ? 0 : 1); c < cols.length; c++) {
						int colSpan = 1;
						int rowSpan = 1;
						if (separator == 'X') {
							if ((c != 0) && (cells[r][c] == cells[r][c-1]))
								continue;
							if ((r != 0) && (cells[r][c] == cells[r-1][c]))
								continue;
							for (int lc = (c+1); lc < cols.length; lc++) {
								if (cells[r][lc] == cells[r][c])
									colSpan++;
								else break;
							}
							for (int lr = (r+1); lr < rows.length; lr++) {
								if (cells[lr][c] == cells[r][c])
									rowSpan++;
								else break;
							}
							appendCellData(page, cells[r][c], separator, colSpan, rowSpan, rowData[r]);
						}
						else {
							appendCellData(page, cells[r][c], separator, colSpan, rowSpan, rowData[r]);
							if (((c+1) < cols.length) || ((tx+1) < tables[ty].length))
								rowData[r].append(separator);
						}
					}
			}
			
			//	store data from grid row
			for (int r = ((ty == 0) ? 0 : 1); r < rowData.length; r++)
				rowDataList.add(rowData[r]);
		}
		
		//	write table data
		StringBuffer tableData = new StringBuffer();
		if (separator == 'X')
			tableData.append("<table>\r\n");
		for (Iterator rdit = rowDataList.iterator(); rdit.hasNext();) {
			StringBuffer rowData = ((StringBuffer) rdit.next());
			if (separator == 'X')
				tableData.append("<tr>\r\n");
			tableData.append(rowData);
			if (separator == 'X')
				tableData.append("</tr>");
			tableData.append("\r\n");
		}
		if (separator == 'X')
			tableData.append("</table>\r\n");
		
		//	finally ...
		return tableData.toString();
	}
	
	/* This will simply repeat content of multi-column or multi-row cells, but
	 * there is little else we can do for plain text formats */
	private static void appendCellData(ImPage page, ImRegion cell, char separator, int colSpan, int rowSpan, StringBuffer data) {
		if ((separator == ',') || (separator == ';'))
			data.append('"');
		else if (separator == 'X')
			data.append("<td" + ((colSpan == 1) ? "" : (" colspan=\"" + colSpan + "\"")) + ((rowSpan == 1) ? "" : (" rowspan=\"" + rowSpan + "\"")) + ">");
		ImWord[] cellWords = page.getWordsInside(cell.bounds);
		if (cellWords.length != 0) {
			Arrays.sort(cellWords, textStreamOrder);
			String cellStr = getString(cellWords[0], cellWords[cellWords.length-1], true);
			if (cellStr.matches("\\.\\s*[0-9]+"))
				cellStr = ("0." + cellStr.substring(".".length()).trim());
			if ((separator == ',') || (separator == ';')) {
				StringBuffer eCellStr = new StringBuffer();
				for (int i = 0; i < cellStr.length(); i++) {
					char ch = cellStr.charAt(i);
					if (ch == '"')
						eCellStr.append('"');
					eCellStr.append(ch);
				}
				cellStr = eCellStr.toString();
			}
			else if (separator == 'X')
				cellStr = AnnotationUtils.escapeForXml(cellStr);
			data.append(cellStr);
		}
		if ((separator == ',') || (separator == ';'))
			data.append('"');
		else if (separator == 'X')
			data.append("</td>\r\n");
	}
	
	private static ImRegion[] getRegionsInside(ImPage page, BoundingBox box, String type, boolean fuzzy) {
		ImRegion[] regions = page.getRegionsInside(box, fuzzy);
		ArrayList regionList = new ArrayList();
		for (int r = 0; r < regions.length; r++) {
			if (type.equals(regions[r].getType()))
				regionList.add(regions[r]);
		}
		return ((ImRegion[]) regionList.toArray(new ImRegion[regionList.size()]));
	}
	
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
}