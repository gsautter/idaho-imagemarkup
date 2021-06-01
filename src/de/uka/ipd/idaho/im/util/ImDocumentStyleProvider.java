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

import java.io.File;
import java.io.IOException;

import de.uka.ipd.idaho.gamta.util.DocumentStyle;
import de.uka.ipd.idaho.gamta.util.DocumentStyle.Data;
import de.uka.ipd.idaho.gamta.util.DocumentStyleProvider;

/**
 * Image Markup specific document style provider, wrapping document style data
 * objects accordingly.
 * 
 * @author sautter
 */
public class ImDocumentStyleProvider extends DocumentStyleProvider {
	
	/** Constructor
	 * @param docStyleListUrl the URL to load the document style template list from
	 */
	public ImDocumentStyleProvider(String docStyleListUrl) {
		super(docStyleListUrl);
	}
	
	/** Constructor
	 * @param docStyleFolder the folder document style templates are stored in
	 * @param docStyleFileSuffix the file name suffix to recognize document
	 *            style template files by (null deactivates file filtering)
	 */
	public ImDocumentStyleProvider(File docStyleFolder, String docStyleFileSuffix) {
		super(docStyleFolder, docStyleFileSuffix);
	}
	
	/** Constructor
	 * @param docStyleListUrl the URL to load the document style template list from
	 * @param docStyleFolder the folder to store document style templates in
	 * @param docStyleFileSuffix the file name suffix to recognize document
	 *            style template files by (null deactivates file filtering)
	 */
	public ImDocumentStyleProvider(String docStyleListUrl, File docStyleFolder, String docStyleFileSuffix) {
		super(docStyleListUrl, docStyleFolder, docStyleFileSuffix);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentStyleProvider#init()
	 */
	public void init() throws IOException {
		ImDocumentStyle.getStyleFor(null); // just need to make sure class is fully initialized
		super.init();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.gamta.util.DocumentStyleProvider#wrapDocumentStyleData(de.uka.ipd.idaho.gamta.util.DocumentStyle.Data)
	 */
	protected DocumentStyle wrapDocumentStyleData(Data data) {
		return new ImDocumentStyle(data);
	}
//	
//	public static void main(String[] args) throws Exception {
////		ImDocumentStyleProvider dsp = new ImDocumentStyleProvider("http://tb.plazi.org/GgServer/DocumentStyles/list.txt");
////		ImDocumentStyleProvider dsp = new ImDocumentStyleProvider("http://tb.plazi.org/GgServer/DocumentStyles/list.txt", new File("E:/GoldenGATEv3/Plugins/DocumentStyleProviderData/Test"), ".docStyle");
//		ImDocumentStyleProvider dsp = new ImDocumentStyleProvider(new File("E:/GoldenGATEv3/Plugins/DocumentStyleProviderData/Test"), ".docStyle");
//		dsp.init();
////		ImDocumentStyle.getStyleFor(null);
////		StandaloneDocumentStyleProvider sdsp = new StandaloneDocumentStyleProvider(new File("E:/GoldenGATEv3/Plugins/DocumentStyleProviderData"));
//		Attributed doc = new AbstractAttributed();
//////		doc.setAttribute(DocumentStyle.DOCUMENT_STYLE_NAME_ATTRIBUTE, "ijsem.0000.journal_article.docStyle");
////		doc.setAttribute(DocumentStyle.DOCUMENT_STYLE_NAME_ATTRIBUTE, "we_dont_have_this.docStyle");
//////		DocumentStyle ds = DocumentStyle.getStyleFor(doc);
//		DocumentStyle ds = DocumentStyle.getStyleFor(doc);
//		System.out.println(ds);
//		System.out.println(Arrays.toString(ds.getSubsetPrefixes()));
//	}
}
