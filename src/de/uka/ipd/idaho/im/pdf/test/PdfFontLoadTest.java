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
package de.uka.ipd.idaho.im.pdf.test;

import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.uka.ipd.idaho.im.util.ImFontUtils;

/**
 * @author sautter
 *
 */
public class PdfFontLoadTest {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		ImFontUtils.loadFreeFonts();
		
		int size = 20;
		int style = Font.BOLD | Font.ITALIC;
		JPanel fonts = new JPanel(new GridLayout(0, 1));
		JLabel serif = new JLabel("AbCdEfGhIjKlMnOpQrStUvWxYz flava");
		serif.setFont(new Font("FreeSerif", style, size));
		fonts.add(serif);
		JLabel sans = new JLabel("AbCdEfGhIjKlMnOpQrStUvWxYz flava");
		sans.setFont(new Font("FreeSans", style, size));
		fonts.add(sans);
		JLabel mono = new JLabel("AbCdEfGhIjKlMnOpQrStUvWxYz flava");
		mono.setFont(new Font("FreeMono", style, size));
		fonts.add(mono);
		JOptionPane.showMessageDialog(null, fonts);
	}
}