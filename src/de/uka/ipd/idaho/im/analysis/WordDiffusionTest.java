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
package de.uka.ipd.idaho.im.analysis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;

import de.uka.ipd.idaho.im.ImDocument;
import de.uka.ipd.idaho.im.ImPage;
import de.uka.ipd.idaho.im.ImWord;
import de.uka.ipd.idaho.im.analysis.Imaging.AnalysisImage;
import de.uka.ipd.idaho.im.util.ImDocumentIO;
import de.uka.ipd.idaho.im.utilities.ImageDisplayDialog;

/**
 * @author sautter
 */
public class WordDiffusionTest {
	//	TODO page 84 in 10.5281zenodo.1481114_vanTol_Guenther_2018.pdf (heading so close over column margin that it blocks split)
	//	TODO try this with some scan, and see how words come out compared to lines and paragraphs (hope: words grow together)
	
	//	TODO https://github.com/gsautter/goldengate-imagine/issues/551 (page 0 fails to slice and dice)
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		//	load document and page image
//		ImDocument doc = ImDocumentIO.loadDocument(new File("E:/Testdaten/PdfExtract/10.5281zenodo.1481114_vanTol_Guenther_2018.pdf.metaTest.imdir"));
//		ImPage page = doc.getPage(84);
		ImDocument doc = ImDocumentIO.loadDocument(new File("E:/Testdaten/PdfExtract/science.362.6417.897.pdf.imdir"));
		ImPage page = doc.getPage(0);
		BufferedImage bi = page.getPageImage().image;
		
		//	black out words
		ImWord[] words = page.getWords();
		Graphics2D gr = bi.createGraphics();
		gr.setColor(Color.BLACK);
		for (int w = 0; w < words.length; w++)
			gr.fillRect(words[w].bounds.left, words[w].bounds.top, words[w].bounds.getWidth(), words[w].bounds.getHeight());
		//	TODO do the same for images
		//	TODO do the same for coherent graphics objects
		
		//	wrap image, and get brightness
		AnalysisImage ai = Imaging.wrapImage(bi, null);
		byte[][] aib = ai.getBrightness();
		byte threshold = 64;
		
		//	gradually diffuse black into white
		int[][] dists = new int[bi.getWidth()][bi.getHeight()];
		LinkedHashSet ptp = new LinkedHashSet();
		for (int c = 0; c < bi.getWidth(); c++)
			for (int r = 0; r < bi.getHeight(); r++) {
				if (aib[c][r] < threshold) {
					dists[c][r] = 0;
					if ((c != 0) && (threshold <= aib[c-1][r]))
						ptp.add(new Point((c-1), r));
					if (((c+1) != bi.getWidth()) && (threshold <= aib[c+1][r]))
						ptp.add(new Point((c+1), r));
					if ((r != 0) && (threshold <= aib[c][r-1]))
						ptp.add(new Point(c, (r-1)));
					if (((r+1) != bi.getHeight()) && (threshold <= aib[c][r+1]))
						ptp.add(new Point(c, (r+1)));
//					//	TODO maybe only do straight neighbors (not diagonal)
//					for (int ec = Math.max(0, (c-1)); ec < Math.min(bi.getWidth(), (c+2)); ec++)
//						for (int er = Math.max(0, (r-1)); er < Math.min(bi.getHeight(), (r+2)); er++) {
//							if (threshold <= aib[ec][er])
//								ptp.add(new Point(ec, er));
//						}
				}
				else dists[c][r] = Integer.MAX_VALUE;
			}
		int dist = 1;
		while (ptp.size() != 0) {
			System.out.println("Got " + ptp.size() + " points at distance " + dist);
			LinkedHashSet nptp = new LinkedHashSet();
			for (Iterator pit = ptp.iterator(); pit.hasNext();) {
				Point p = ((Point) pit.next());
				dists[p.x][p.y] = dist;
				if ((p.x != 0) && (dists[p.x-1][p.y] == Integer.MAX_VALUE))
					nptp.add(new Point((p.x-1), p.y));
				if (((p.x+1) != bi.getWidth()) && (dists[p.x+1][p.y] == Integer.MAX_VALUE))
					nptp.add(new Point((p.x+1), p.y));
				if ((p.y != 0) && (dists[p.x][p.y-1] == Integer.MAX_VALUE))
					nptp.add(new Point(p.x, (p.y-1)));
				if (((p.y+1) != bi.getHeight()) && (dists[p.x][p.y+1] == Integer.MAX_VALUE))
					nptp.add(new Point(p.x, (p.y+1)));
//				//	TODO maybe only do straight neighbors (not diagonal)
//				for (int ec = Math.max(0, (p.x-1)); ec < Math.min(bi.getWidth(), (p.x+2)); ec++)
//					for (int er = Math.max(0, (p.y-1)); er < Math.min(bi.getHeight(), (p.y+2)); er++) {
//						if (dists[ec][er] == Integer.MAX_VALUE)
//							nptp.add(new Point(ec, er));
//					}
			}
			nptp.removeAll(ptp);
			ptp = nptp;
			dist++;
		}
		
		//	visualize result
		for (int c = 0; c < bi.getWidth(); c++) {
			for (int r = 0; r < bi.getHeight(); r++)
				bi.setRGB(c, r, getColor(dists[c][r], dist));
		}
		
		//	show result
		ImageDisplayDialog idd = new ImageDisplayDialog("Document " + doc.getAttribute(ImDocument.DOCUMENT_NAME_ATTRIBUTE));
		idd.addImage(bi, ("Page " + page.pageId));
		idd.setSize(1200, 800);
		idd.setLocationRelativeTo(null);
		idd.setVisible(true);
	}
	
	private static int getColor(int dist, int maxDist) {
		if (dist == 0)
			return Color.BLACK.getRGB();
//		float q = (((float) dist) / maxDist);
		float q = ((float) Math.sqrt(((float) dist) / maxDist));
//		float q = ((float) Math.sqrt(Math.sqrt(((float) dist) / maxDist)));
//		float q = ((float) Math.sqrt(Math.sqrt(Math.sqrt(((float) dist) / maxDist))));
		float h = q;
		float s = 0.7f;
		float b = (0.5f + (q / 2));
		return Color.HSBtoRGB(h, s, b);
	}
	
	private static class Point {
		final int x;
		final int y;
		Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
		public int hashCode() {
			return ((this.x << 16) | (this.y << 0));
		}
		public boolean equals(Object obj) {
			Point pt = ((Point) obj);
			return ((pt.x == this.x) && (pt.y == this.y));
		}
		public String toString() {
			return ("[" + this.x + "," + this.y + "]");
		}
	}
}
