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
package de.uka.ipd.idaho.im;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeSet;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.gamta.util.imaging.PageImage;

/**
 * A region in an image markup document, e.g. a block, a column, or a table.
 * 
 * @author sautter
 */
public class ImRegion extends ImLayoutObject {
	
	/** the region type for marking tables, namely 'table', intentionally distinct from HTML counterpart */
	public static final String TABLE_TYPE = "table";
	
	/** the region type for marking table rows, namely 'tableRow', intentionally distinct from HTML counterpart */
	public static final String TABLE_ROW_TYPE = "tableRow";
	
	/** the region type for marking table columns, namely 'tableCol' */
	public static final String TABLE_COL_TYPE = "tableCol";
	
	/** the region type for marking table cells, namely 'tableCell', intentionally distinct from HTML counterpart */
	public static final String TABLE_CELL_TYPE = "tableCell";
	
	/** the region type for marking bitmap based images, diagrams, maps, etc., namely 'image' */
	public static final String IMAGE_TYPE = "image";
	
	/** the region type for marking vector based drawings and diagrams, namely 'graphics' */
	public static final String GRAPHICS_TYPE = "graphics";
	
	private ImPage page;
	
	/** Constructor (automatically adds the region to the argument page; if
	 * this is undesired for whatever reason, one of the constructors taking
	 * a document and a page ID as arguments has to be used instead)
	 * @param page the page the region lies in
	 * @param bounds the bounding box defining the region
	 */
	public ImRegion(ImPage page, BoundingBox bounds) {
		this(page.getDocument(), page.pageId, bounds, null);
		page.addRegion(this);
	}
	
	/** Constructor (automatically adds the region to the argument page; if
	 * this is undesired for whatever reason, one of the constructors taking
	 * a document and a page ID as arguments has to be used instead)
	 * @param page the page the region lies in
	 * @param bounds the bounding box defining the region
	 * @param type the region type
	 */
	public ImRegion(ImPage page, BoundingBox bounds, String type) {
		this(page.getDocument(), page.pageId, bounds, type);
		page.addRegion(this);
	}
	
	/** Constructor
	 * @param doc the document the region belongs to
	 * @param pageId the ID of the page the region lies in
	 * @param bounds the bounding box defining the region
	 */
	public ImRegion(ImDocument doc, int pageId, BoundingBox bounds) {
		this(doc, pageId, bounds, null);
	}
	
	/** Constructor
	 * @param doc the document the region belongs to
	 * @param pageId the ID of the page the region lies in
	 * @param bounds the bounding box defining the region
	 * @param type the region type
	 */
	public ImRegion(ImDocument doc, int pageId, BoundingBox bounds, String type) {
		super(doc, pageId, bounds);
		if (type != null)
			this.setType(type);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getPage()
	 */
	public ImPage getPage() {
		return this.page;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#setPage(de.uka.ipd.idaho.im.ImPage)
	 */
	public void setPage(ImPage page) {
		this.page = page;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.im.ImLayoutObject#getImage()
	 */
	public PageImage getImage() {
		return this.getPageImage().getSubImage(this.bounds, true);
	}
	
	/**
	 * Retrieve all layout words in the region. If the region is detached from
	 * the document, this method returns an empty array.
	 * @return an array holding the words
	 */
	public ImWord[] getWords() {
		return ((this.page == null) ? new ImWord[0] : this.page.getWordsInside(this.bounds));
	}
	
	/**
	 * Retrieve all layout words that are the first of a logical text stream in
	 * the region. This includes both words that do not have a predecessor and
	 * ones whose predecessor lies outside the region. If the region boundary
	 * cuts through text lines, this method can also return multiple words from
	 * the same actual logical text stream, namely if their immediate
	 * predecessors lie outside the region. If the region is detached from the
	 * document, this method returns an empty array.
	 * @return an array holding the text stream heads
	 */
	public ImWord[] getTextStreamHeads() {
		if (this.page == null)
			return new ImWord[0];
		ImWord[] words = this.page.getWordsInside(this.bounds);
		LinkedList tsh = new LinkedList();
		for (int w = 0; w < words.length; w++) {
			ImWord pw = words[w].getPreviousWord();
			if ((pw == null) || (pw.pageId != words[w].pageId) || (pw.centerX < this.bounds.left) || (this.bounds.right < pw.centerX) || (pw.centerY < this.bounds.top) || (this.bounds.bottom < pw.centerY))
				tsh.addLast(words[w]);
		}
		return ((ImWord[]) tsh.toArray(new ImWord[tsh.size()]));
	}
	
	/**
	 * Retrieve the regions (e.g. paragraphs and lines) nested inside this
	 * region. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. In addition, they are sorted
	 * according to the orientation of the document, e.g. left to right and top
	 * to bottom for text in Latin scripts. If the region is detached from the
	 * document, this method returns an empty array.
	 * @return an array holding the regions nested in this region
	 */
	public ImRegion[] getRegions() {
		return this.getRegions(null);
	}
	
	/**
	 * Retrieve the regions (e.g. paragraphs or lines) nested inside this
	 * region. Regions higher up in the hierarchy appear before the ones
	 * nested in them in the returned array. In addition, they are sorted
	 * according to the orientation of the document, e.g. left to right and top
	 * to bottom for text in Latin scripts. If the region is detached from the
	 * document, this method returns an empty array.
	 * @param type the type of regions to return
	 * @return an array holding the regions representing the layout of the page
	 */
	public ImRegion[] getRegions(String type) {
		if (this.page == null)
			return new ImRegion[0];
		ImRegion[] regions = this.page.getRegionsInside(this.bounds, false);
		if (type == null)
			return regions;
		ArrayList regionList = new ArrayList();
		for (int r = 0; r < regions.length; r++) {
			if (type.equals(regions[r].getType()))
				regionList.add(regions[r]);
		}
		return ((ImRegion[]) regionList.toArray(new ImRegion[regionList.size()]));
	}
	
	/**
	 * Retrieve the types of regions present in this page. The region types are
	 * in lexicographical order. If the region is detached from the document,
	 * this method returns an empty array.
	 * @return an array holding the region types
	 */
	public String[] getRegionTypes() {
		if (this.page == null)
			return new String[0];
		ImRegion[] regions = this.page.getRegionsInside(this.bounds, false);
		TreeSet regionTypes = new TreeSet();
		for (int r = 0; r < regions.length; r++)
			regionTypes.add(regions[r].getType());
		return ((String[]) regionTypes.toArray(new String[regionTypes.size()]));
	}
}