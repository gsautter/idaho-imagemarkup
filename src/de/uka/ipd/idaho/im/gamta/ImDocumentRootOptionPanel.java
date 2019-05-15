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
package de.uka.ipd.idaho.im.gamta;

import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;

/**
 * Configuration widget for ImTokenSequence and ImDocumentRoot.
 * 
 * @author sautter
 */
public class ImDocumentRootOptionPanel extends JPanel {
	private static final String RAW_NORMALIZATION_LEVEL = "Raw (words strictly in layout order)";
	private static final String WORD_NORMALIZATION_LEVEL = "Words (words in layout order, but de-hyphenated)";
	private static final String PARAGRAPH_NORMALIZATION_LEVEL = "Paragraphs (logical paragraphs kept together)";
	private static final String STREAM_NORMALIZATION_LEVEL = "Text Streams (logical text streams one after another)";
	private static final String[] NORMALIZATION_LEVELS = {
		RAW_NORMALIZATION_LEVEL,
		WORD_NORMALIZATION_LEVEL,
		PARAGRAPH_NORMALIZATION_LEVEL,
		STREAM_NORMALIZATION_LEVEL,
	};
	
	private JComboBox normalizationLevel = new JComboBox(NORMALIZATION_LEVELS);
	private JCheckBox normalizeChars = new JCheckBox("Normalize Characters");
	
	private JCheckBox excludeTables = new JCheckBox("Exclude Tables");
	private JCheckBox excludeCaptionsFootnotes = new JCheckBox("Exclude Captions & Footnotes");
	private JCheckBox showWordsAnnotations = new JCheckBox("Show Word Annotations");
	
	/** Constructor
	 */
	public ImDocumentRootOptionPanel() {
		this(0);
	}
	
	/** Constructor
	 * @param flags the initial configuration flags
	 */
	public ImDocumentRootOptionPanel(int flags) {
		super(new GridLayout(0, 1), true);
		
		int normalizationLevel = (flags & ImDocumentRoot.NORMALIZATION_LEVEL_STREAMS);
		if (normalizationLevel == ImDocumentRoot.NORMALIZATION_LEVEL_RAW)
			this.normalizationLevel.setSelectedItem(RAW_NORMALIZATION_LEVEL);
		else if (normalizationLevel == ImDocumentRoot.NORMALIZATION_LEVEL_WORDS)
			this.normalizationLevel.setSelectedItem(WORD_NORMALIZATION_LEVEL);
		else if (normalizationLevel == ImDocumentRoot.NORMALIZATION_LEVEL_STREAMS)
			this.normalizationLevel.setSelectedItem(STREAM_NORMALIZATION_LEVEL);
		else this.normalizationLevel.setSelectedItem(PARAGRAPH_NORMALIZATION_LEVEL);
		this.normalizeChars.setSelected((flags & ImDocumentRoot.NORMALIZE_CHARACTERS) != 0);
		this.excludeTables.setSelected((flags & ImDocumentRoot.EXCLUDE_TABLES) != 0);
		this.excludeCaptionsFootnotes.setSelected((flags & ImDocumentRoot.EXCLUDE_CAPTIONS_AND_FOOTNOTES) != 0);
		this.showWordsAnnotations.setSelected((flags & ImDocumentRoot.SHOW_TOKENS_AS_WORD_ANNOTATIONS) != 0);
		
		this.add(new JLabel("Use Document Normalization Level ...", JLabel.LEFT));
		this.add(this.normalizationLevel);
		this.add(this.normalizeChars);
		this.add(this.excludeTables);
		this.add(this.excludeCaptionsFootnotes);
		this.add(this.showWordsAnnotations);
	}
	
	/**
	 * Return the integer encoded configuration flags.
	 * @return the integer encoded configuration flags
	 */
	public int getFlags() {
		
		int flags;
		if (RAW_NORMALIZATION_LEVEL.equals(this.normalizationLevel.getSelectedItem()))
			flags = ImDocumentRoot.NORMALIZATION_LEVEL_RAW;
		else if (WORD_NORMALIZATION_LEVEL.equals(this.normalizationLevel.getSelectedItem()))
			flags = ImDocumentRoot.NORMALIZATION_LEVEL_WORDS;
		else if (STREAM_NORMALIZATION_LEVEL.equals(this.normalizationLevel.getSelectedItem()))
			flags = ImDocumentRoot.NORMALIZATION_LEVEL_STREAMS;
		else flags = ImDocumentRoot.NORMALIZATION_LEVEL_PARAGRAPHS;
		
		if (this.normalizeChars.isSelected())
			flags |= ImDocumentRoot.NORMALIZE_CHARACTERS;
		if (this.excludeTables.isSelected())
			flags |= ImDocumentRoot.EXCLUDE_TABLES;
		if (this.excludeCaptionsFootnotes.isSelected())
			flags |= ImDocumentRoot.EXCLUDE_CAPTIONS_AND_FOOTNOTES;
		if (this.showWordsAnnotations.isSelected())
			flags |= ImDocumentRoot.SHOW_TOKENS_AS_WORD_ANNOTATIONS;
		
		return flags;
	}
	
	/**
	 * Show a prompt for customizing the flags for an ImDocumentRoot. If the
	 * prompt is closed by any other means than the OK option, this method
	 * returns -1;
	 * @param flags the flags to initialize the prompt with
	 * @param title the title for the prompt
	 * @return the flags selected in the prompt
	 */
	public static int showOptionDialog(int flags, String title) {
		ImDocumentRootOptionPanel idrop = new ImDocumentRootOptionPanel(flags);
		int choice = DialogFactory.confirm(idrop, title, JOptionPane.OK_CANCEL_OPTION);
		return ((choice == JOptionPane.OK_OPTION) ? idrop.getFlags() : -1);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(showOptionDialog(0, "ImDocumentRoot Options"));
	}
}
