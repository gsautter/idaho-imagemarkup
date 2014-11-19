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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.im.ImAnnotation;
import de.uka.ipd.idaho.im.ImWord;

/**
 * Utility library for modifying Image Markup documents.
 * 
 * @author sautter
 */
public class ImUtils implements ImagingConstants {
	
	/** Comparator sorting ImWords in layout order, i.e., top to bottom and
	 * left to right. With arrays or collections whose content objects are NOT
	 * ImWords, using this comparator results in ClassCastExceptions. */
	public static Comparator leftRightTopDownOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			ImWord w1 = ((ImWord) obj1);
			ImWord w2 = ((ImWord) obj2);
			
			//	check non-overlapping cases first
			if (w1.bounds.bottom <= w2.bounds.top)
				return -1;
			if (w2.bounds.bottom <= w1.bounds.top)
				return 1;
			if (w1.bounds.right <= w2.bounds.left)
				return -1;
			if (w2.bounds.right <= w1.bounds.left)
				return 1;
			
			//	now, we have overlap (more likely within lines than between)
			if (w1.centerY <= w2.bounds.top)
				return -1;
			if (w2.centerY <= w1.bounds.top)
				return 1;
			if (w1.centerX <= w2.bounds.left)
				return -1;
			if (w2.centerX <= w1.bounds.left)
				return 1;
			
			//	now, either center lies in the other box (massive overlap, not peripheral)
			return ((w1.centerY == w2.centerY) ? (w1.centerX - w2.centerX) : (w1.centerY - w2.centerY));
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
			
			//	check text stream IDs first
			int c = imw1.getTextStreamId().compareTo(imw2.getTextStreamId());
			if (c != 0)
				return c;
			
			//	check page IDs
			c = (imw1.pageId - imw2.pageId);
			if (c != 0)
				return c;
			
			//	check text stream position
			return (imw1.getTextStreamPos() - imw2.getTextStreamPos());
		}
	};
	
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
//		JPanel jp = new JPanel(new BorderLayout());
//        JComboBox jcb = new JComboBox(existingTypes);
//        jcb.setEditable(allowInput);
//        if (existingType != null)
//        	jcb.setSelectedItem(existingType);
//        jp.add(new JLabel(text + " "), BorderLayout.WEST);
//        jp.add(jcb, BorderLayout.CENTER);
//		JPanel jpc = new JPanel(new BorderLayout());
//		jpc.add(jp, BorderLayout.NORTH);
//        jpc.setPreferredSize(new Dimension(400, 23));
//        if (JOptionPane.showConfirmDialog(DialogFactory.getTopWindow(), jpc, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
//        	return null;
//		Object typeObj = jcb.getSelectedItem();
//		if (typeObj == null)
//			return null;
//		String type = typeObj.toString().trim();
//		if (type.length() == 0)
//			return null;
//		if (AnnotationUtils.isValidAnnotationType(type))
//			return type;
//		else {
//			JOptionPane.showMessageDialog(DialogFactory.getTopWindow(), ("'" + type.toString().trim() + "' is not a valid type."), "Invalid Type", JOptionPane.ERROR_MESSAGE);
//			return null;
//		}
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
		ssp.addSelector(textNew, existingTypes, existingType, false);
		if (ssp.prompt(DialogFactory.getTopWindow()) != JOptionPane.OK_OPTION)
			return null;
		String typeOld = ssp.typeOrNameAt(0, false);
		String typeNew = ssp.typeOrNameAt(1, true);
		if ((typeOld == null) || (typeNew == null) || typeOld.equals(typeNew))
			return null;
		return new StringPair(typeOld, typeNew);
//		JPanel jpOld = new JPanel(new BorderLayout());
//		JComboBox jcbOld = new JComboBox(existingTypes);
//		if (existingType != null)
//			jcbOld.setSelectedItem(existingType);
//		jpOld.add(new JLabel(textOld + " "), BorderLayout.WEST);
//		jpOld.add(jcbOld, BorderLayout.CENTER);
//		
//		JPanel jpNew = new JPanel(new BorderLayout());
//		JComboBox jcbNew = new JComboBox(existingTypes);
//		jcbNew.setEditable(allowInput);
//		if (existingType != null)
//			jcbNew.setSelectedItem(existingType);
//		jpNew.add(new JLabel(textOld + " "), BorderLayout.WEST);
//		jpNew.add(jcbNew, BorderLayout.CENTER);
//		
//		JPanel jpc = new JPanel(new BorderLayout());
//		jpc.add(jpOld, BorderLayout.NORTH);
//		jpc.add(jpNew, BorderLayout.SOUTH);
//		jpc.setPreferredSize(new Dimension(400, 48));
//        
//        if (JOptionPane.showConfirmDialog(DialogFactory.getTopWindow(), jpc, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
//        	return null;
//		Object typeObjOld = jcbOld.getSelectedItem();
//		Object typeObjNew = jcbNew.getSelectedItem();
//		if ((typeObjOld == null) || (typeObjNew == null))
//			return null;
//		String typeOld = typeObjOld.toString().trim();
//		String typeNew = typeObjNew.toString().trim();
//		if ((typeOld.length() == 0) || (typeNew.length() == 0) || typeOld.equals(typeNew))
//			return null;
//		else if (AnnotationUtils.isValidAnnotationType(typeOld) && AnnotationUtils.isValidAnnotationType(typeNew))
//			return new StringPair(typeOld, typeNew);
//		else {
//			JOptionPane.showMessageDialog(DialogFactory.getTopWindow(), ("'" + typeNew.toString().trim() + "' is not a valid type."), "Invalid Type", JOptionPane.ERROR_MESSAGE);
//			return null;
//		}
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
			return JOptionPane.showConfirmDialog(parent, this, this.title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
				JOptionPane.showMessageDialog(DialogFactory.getTopWindow(), ("'" + typeOrName + "' is not a valid type or name."), "Invalid Type Or Name", JOptionPane.ERROR_MESSAGE);
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
	 * Order a series of words a separate logical text stream. The words in the
	 * argument array need not belong to an individual logical text stream, nor
	 * need they be single chunks of the logical text streams involved. The
	 * predecessor of the first word is set to the the last predecessor of any
	 * word from the array that is not contained in the array itself, the
	 * successor of the last word is set to the first successor of any word
	 * from the array that is not contained in the array itself.
	 * @param words the words to make a text stream
	 * @param wordOrder the word order to apply
	 */
	public static void orderStream(ImWord[] words, Comparator wordOrder) {
		
		//	anything to work with?
		if (words.length < 2)
			return;
		
		//	order words
		Arrays.sort(words, wordOrder);
		
		//	index words from array
		HashSet wordIDs = new HashSet();
		for (int w = 0; w < words.length; w++)
			wordIDs.add(words[w].getLocalID());
		
		//	find predecessor and successor
		ArrayList predecessors = new ArrayList();
		ArrayList successors = new ArrayList();
		for (int w = 0; w < words.length; w++) {
			ImWord prev = words[w].getPreviousWord();
			if ((prev != null) && !wordIDs.contains(prev.getLocalID()))
				predecessors.add(prev);
			ImWord next = words[w].getNextWord();
			if ((next != null) && !wordIDs.contains(next.getLocalID()))
				successors.add(next);
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
			if (words[0].getPreviousWord() != prev)
				words[0].setPreviousWord(prev);
		}
		Collections.sort(successors, wordOrder);
		if (successors.size() != 0) {
			ImWord next = ((ImWord) successors.get(0));
			if (words[words.length-1].getNextWord() != next)
				words[words.length-1].setNextWord(next);
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
			if ((imw.getNextRelation() == ImWord.NEXT_RELATION_SEPARATE) && (imw.getNextWord() != null) && Gamta.insertSpace(imw.getString(), imw.getNextWord().getString()))
				sb.append(" ");
			else if (imw.getNextRelation() == ImWord.NEXT_RELATION_PARAGRAPH_END)
				sb.append(ignoreLineBreaks ? " " : "\r\n");
			else if ((imw.getNextRelation() == ImWord.NEXT_RELATION_HYPHENATED) && (sb.length() != 0))
				sb.deleteCharAt(sb.length()-1);
		}
		return sb.toString();
	}
}