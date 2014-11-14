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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

import de.uka.ipd.idaho.gamta.util.imaging.ImagingConstants;
import de.uka.ipd.idaho.im.ImWord;

/**
 * Editor dialog for individual words, used shared in both IM document editor
 * and IM image editor.
 * 
 * @author sautter
 */
class EditWordDialog extends JDialog implements ImagingConstants {
	private ImWord word;
	
	private JTextField wordString;
	private JCheckBox bold;
	private JCheckBox italics;
	
	private Color wordColor;
	private SymbolTable symbolTable;
	
	private boolean committed = false;
	
	EditWordDialog(Frame owner, ImWord word, Color wordColor, boolean modal) {
		super(owner, "", modal);
		this.word = word;
		this.wordColor = wordColor;
		this.init();
	}
	EditWordDialog(Dialog owner, ImWord word, Color wordColor, boolean modal) {
		super(owner, "", modal);
		this.word = word;
		this.wordColor = wordColor;
		this.init();
	}
	
	private void init() {
		this.setUndecorated(true);
		Dimension size = new Dimension(Math.max((this.word.bounds.right - this.word.bounds.left), 50), ((this.word.bounds.bottom - this.word.bounds.top) + 65));
		System.out.println("EditWordDialog size is " + size);
		this.setSize(size);
		this.getContentPane().setLayout(new BorderLayout());
		
		final BufferedImage wi = this.word.getImage().image;
		JPanel ip = new JPanel() {
			public void paint(Graphics g) {
				super.paint(g);
				g.drawImage(wi, 0, 0, null);
			}
			public Dimension getPreferredSize() {
				return new Dimension((word.bounds.right - word.bounds.left), (word.bounds.bottom - word.bounds.top));
			}
			public Dimension getMaximumSize() {
				return this.getPreferredSize();
			}
			public Dimension getMinimumSize() {
				return this.getPreferredSize();
			}
			public Dimension getSize() {
				return this.getPreferredSize();
			}
		};
		ip.setBackground(Color.WHITE);
		
		this.bold = new JCheckBox("Bold", this.word.hasAttribute(BOLD_ATTRIBUTE));
		this.bold.setToolTipText("Bold");
		this.bold.setBackground(Color.WHITE);
		this.bold.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 2), BorderFactory.createLineBorder(this.wordColor, 1)));
		this.italics = new JCheckBox("Italics", this.word.hasAttribute(ITALICS_ATTRIBUTE));
		this.italics.setToolTipText("Italics");
		this.italics.setBackground(Color.WHITE);
		this.italics.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 2), BorderFactory.createLineBorder(this.wordColor, 1)));
		
		this.wordString = new JTextField(this.word.getString());
		this.wordString.setCaretPosition(this.word.getString().length());
		this.wordString.setFont(wordString.getFont().deriveFont(14f));
		this.wordString.setPreferredSize(new Dimension((this.word.bounds.right - this.word.bounds.left), 23));
		this.wordString.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(this.wordColor, 1), BorderFactory.createLoweredBevelBorder()));
		this.wordString.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent ke) {
				if (ke.getKeyCode() == KeyEvent.VK_ESCAPE)
					dispose();
				else if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
					word.setString(wordString.getText().trim());
					if (bold.isSelected() != word.hasAttribute(BOLD_ATTRIBUTE)) {
						if (bold.isSelected())
							word.setAttribute(BOLD_ATTRIBUTE);
						else word.removeAttribute(BOLD_ATTRIBUTE);
					}
					if (italics.isSelected() != word.hasAttribute(ITALICS_ATTRIBUTE)) {
						if (italics.isSelected())
							word.setAttribute(ITALICS_ATTRIBUTE);
						else word.removeAttribute(ITALICS_ATTRIBUTE);
					}
					committed = true;
					dispose();
				}
				else if (ke.isControlDown()) {
					if (ke.getKeyCode() == KeyEvent.VK_I)
						italics.setSelected(!italics.isSelected());
					else if (ke.getKeyCode() == KeyEvent.VK_B)
						bold.setSelected(!bold.isSelected());
				}
			}
		});
		
		JButton sym = new JButton("S" + ((size.width < 75) ? "" : "ym") + ((size.width < 150) ? "" : "bols"));
		sym.setToolTipText("Symbol Table");
		sym.setBackground(Color.WHITE);
		sym.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 1), BorderFactory.createLineBorder(this.wordColor, 1)));
		sym.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				if (symbolTable == null)
					symbolTable = SymbolTable.getSharedSymbolTable();
					symbolTable.setOwner(new SymbolTable.Owner() {
						public void useSymbol(char symbol) {
							StringBuffer sb = new StringBuffer(EditWordDialog.this.wordString.getText());
							int cp = EditWordDialog.this.wordString.getCaretPosition();
							sb.insert(cp, symbol);
							EditWordDialog.this.wordString.setText(sb.toString());
							EditWordDialog.this.wordString.setCaretPosition(++cp);
						}
						public void symbolTableClosed() {
							symbolTable = null;
						}
						public Dimension getSize() {
							return EditWordDialog.this.getSize();
						}
						public Point getLocation() {
							return EditWordDialog.this.getLocation();
						}
						public Color getColor() {
							return EditWordDialog.this.wordColor;
						}
					});
				symbolTable.open();
			}
		});
		JPanel dp = new JPanel(new BorderLayout(), true);
		dp.add(this.wordString, BorderLayout.CENTER);
		dp.add(sym, BorderLayout.EAST);
		
		JButton ok = new JButton("OK");
		ok.setToolTipText("OK");
		ok.setBackground(Color.WHITE);
		ok.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 2), BorderFactory.createLineBorder(this.wordColor, 1)));
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				word.setString(wordString.getText().trim());
				if (bold.isSelected() != word.hasAttribute(BOLD_ATTRIBUTE)) {
					if (bold.isSelected())
						word.setAttribute(BOLD_ATTRIBUTE);
					else word.removeAttribute(BOLD_ATTRIBUTE);
				}
				if (italics.isSelected() != word.hasAttribute(ITALICS_ATTRIBUTE)) {
					if (italics.isSelected())
						word.setAttribute(ITALICS_ATTRIBUTE);
					else word.removeAttribute(ITALICS_ATTRIBUTE);
				}
				committed = true;
				dispose();
			}
		});
		JButton esc = new JButton("Cancel");
		esc.setToolTipText("Cancel");
		esc.setBackground(Color.WHITE);
		esc.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 2), BorderFactory.createLineBorder(this.wordColor, 1)));
		esc.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				dispose();
			}
		});
		
		JPanel sbp = new JPanel(new GridLayout(2, 2));
		sbp.add(this.bold);
		sbp.add(this.italics);
		sbp.add(ok);
		sbp.add(esc);
		this.getContentPane().add(ip, BorderLayout.NORTH);
		this.getContentPane().add(dp, BorderLayout.CENTER);
		this.getContentPane().add(sbp, BorderLayout.SOUTH);
	}
	public void dispose() {
		if (this.symbolTable != null)
			this.symbolTable.close();
		super.dispose();
	}
	boolean isCommitted() {
		return this.committed;
	}
}