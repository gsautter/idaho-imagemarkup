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

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import de.uka.ipd.idaho.gamta.util.imaging.BoundingBox;
import de.uka.ipd.idaho.im.ImLayoutObject;
import de.uka.ipd.idaho.im.ImObject;

/**
 * Utility class transforming Image Markup objects, in particular translating
 * and quadrant rotating bounding boxes as well as attribute values indicating
 * in-page locations or directions.
 * 
 * @author sautter
 */
public class ImObjectTransformer {
	
	/** constant for 0 degrees, indicating no rotation */
	public static final int NO_ROTATION = 0;
	
	/** constant for 90 degrees, indicating clockwise rotation */
	public static final int CLOCKWISE_ROTATION = 90;
	
	/** constant for 90 degrees, indicating counter-clockwise rotation */
	public static final int COUNTER_CLOCKWISE_ROTATION = -90;
	
	/** the ID of the page to transform objects from (mainly for attribute transformations) */
	public final int fromPageId;
	
	/** the bounding box to transform objects from */
	public final BoundingBox fromBounds;
	
	/** the ID of the page to transform objects to (mainly for attribute transformations)  */
	public final int toPageId;
	
	/** the bounding box to transform objects to */
	public final BoundingBox toBounds;
	
	/** the number of degrees the bounding boxes are turned against one another (values can be -90, 0, and 90) */
	public final int turnDegrees;
	
	private int fromCenterX;
	private int fromCenterY;
	private int toCenterX;
	private int toCenterY;
	
	/**
	 * @param fromPageId the ID of the page to transform objects from
	 * @param fromBounds the bounding box to transform objects from
	 * @param toPageId the ID of the page to transform objects to
	 * @param toBounds the bounding box to transform objects to
	 * @param turnDegrees the number of degrees the bounding boxes are turned
	 *            against one another (permitted values are -90, 0, and 90)
	 */
	public ImObjectTransformer(int fromPageId, BoundingBox fromBounds, int toPageId, BoundingBox toBounds, int turnDegrees) {
		this.fromPageId = fromPageId;
		this.fromBounds = fromBounds;
		this.fromCenterX = ((fromBounds.left + fromBounds.right) / 2);
		this.fromCenterY = ((fromBounds.top + fromBounds.bottom) / 2);
		this.toPageId = toPageId;
		this.toBounds = toBounds;
		this.toCenterX = ((toBounds.left + toBounds.right) / 2);
		this.toCenterY = ((toBounds.top + toBounds.bottom) / 2);
		this.turnDegrees = turnDegrees;
		if (this.turnDegrees == NO_ROTATION) {}
		else if (this.turnDegrees == CLOCKWISE_ROTATION) {}
		else if (this.turnDegrees == COUNTER_CLOCKWISE_ROTATION) {}
		else throw new IllegalArgumentException("Can only rotate by 90° (clockwise) or -90° (counter-clockwise), not by " + turnDegrees);
	}
	
	private ImObjectTransformer(ImObjectTransformer inverse) {
		this.fromPageId = inverse.toPageId;
		this.fromBounds = inverse.toBounds;
		this.fromCenterX = inverse.toCenterX;
		this.fromCenterY = inverse.toCenterY;
		this.toPageId = inverse.fromPageId;
		this.toBounds = inverse.fromBounds;
		this.toCenterX = inverse.fromCenterX;
		this.toCenterY = inverse.fromCenterY;
		this.turnDegrees = -inverse.turnDegrees;
	}
	
	/**
	 * Create a transformer that inverts the transformations of this
	 * transformer.
	 * @return the inverted transformer
	 */
	public ImObjectTransformer invert() {
		ImObjectTransformer inverse = new ImObjectTransformer(this);
//		for (int t = 0; t < this.attributeTransformers.size(); t++)
//			inverse.addAttributeTransformer(((AttributeTransformer) this.attributeTransformers.get(t)).invert());
		return inverse;
	}
	
	/**
	 * Transform a bounding box.
	 * @param bounds the bounding box to transform
	 * @return the transformed bounding box
	 */
	public BoundingBox transformBounds(BoundingBox bounds) {
		if (bounds == null)
			return null;
		//	transforming center point is more elegant, but prone to rounding errors, and we want that round trip accurate to the pixel !!!
		Point2D bLeftTop = new Point2D.Float(bounds.left, bounds.top);
		Point2D bRightBottom = new Point2D.Float(bounds.right, bounds.bottom);
		if (this.turnDegrees == NO_ROTATION) {
			Point2D.Float rLeftTop = this.transformPoint(bLeftTop);
			Point2D.Float rRightBottom = this.transformPoint(bRightBottom);
			return new BoundingBox(Math.round(rLeftTop.x), Math.round(rRightBottom.x), Math.round(rLeftTop.y), Math.round(rRightBottom.y));
		}
		else if (this.turnDegrees == CLOCKWISE_ROTATION) {
			Point2D.Float rRightTop = this.transformPoint(bLeftTop);
			Point2D.Float rLeftBottom = this.transformPoint(bRightBottom);
			return new BoundingBox(Math.round(rLeftBottom.x), Math.round(rRightTop.x), Math.round(rRightTop.y), Math.round(rLeftBottom.y));
		}
		else if (this.turnDegrees == COUNTER_CLOCKWISE_ROTATION) {
			Point2D.Float rLeftBottom = this.transformPoint(bLeftTop);
			Point2D.Float rRightTop = this.transformPoint(bRightBottom);
			return new BoundingBox(Math.round(rLeftBottom.x), Math.round(rRightTop.x), Math.round(rRightTop.y), Math.round(rLeftBottom.y));
		}
		else throw new IllegalArgumentException("Can only rotate by 90° (clockwise) or -90° (counter-clockwise), not by " + turnDegrees);
	}
	
	/**
	 * Rotate an image by the number of degrees configured for the transformer.
	 * @param bi the image to transform
	 * @return the transformed image
	 */
	public BufferedImage transformImage(BufferedImage bi) {
		return transformImage(bi, this.turnDegrees);
	}
	
	/**
	 * Rotate an image by the number of degrees configured for the transformer.
	 * @param bi the image to transform
	 * @return the transformed image
	 */
	public static BufferedImage transformImage(BufferedImage bi, int turnDegrees) {
		if (turnDegrees == 0)
			return bi;
//		BufferedImage tBi = new BufferedImage(bi.getHeight(), bi.getWidth(), bi.getType());
//		Graphics2D tBiGr = tBi.createGraphics();
//		tBiGr.translate(((tBi.getWidth() - bi.getWidth()) / 2), ((tBi.getHeight() - bi.getHeight()) / 2));
		BufferedImage tBi;
		Graphics2D tBiGr;
		if ((turnDegrees == CLOCKWISE_ROTATION) || (turnDegrees == COUNTER_CLOCKWISE_ROTATION)) {
			tBi = new BufferedImage(bi.getHeight(), bi.getWidth(), bi.getType());
			tBiGr = tBi.createGraphics();
			tBiGr.translate(((tBi.getWidth() - bi.getWidth()) / 2), ((tBi.getHeight() - bi.getHeight()) / 2));
		}
		else {
			tBi = new BufferedImage(bi.getWidth(), bi.getHeight(), bi.getType());
			tBiGr = tBi.createGraphics();
		}
		if (turnDegrees == CLOCKWISE_ROTATION)
			tBiGr.rotate((Math.PI / 2), (bi.getWidth() / 2), (bi.getHeight() / 2));
		else if (turnDegrees == COUNTER_CLOCKWISE_ROTATION)
			tBiGr.rotate(-(Math.PI / 2), (bi.getWidth() / 2), (bi.getHeight() / 2));
		else if (turnDegrees == (CLOCKWISE_ROTATION + CLOCKWISE_ROTATION))
			tBiGr.rotate(Math.PI, (bi.getWidth() / 2), (bi.getHeight() / 2));
		else throw new IllegalArgumentException("Can only rotate by 90° (clockwise), -90° (counter-clockwise), or 180°, not by " + turnDegrees);
		tBiGr.drawImage(bi, 0, 0, null);
		return tBi;
	}
	
	/**
	 * Get the rotation angle to upright/horizontal orientation for a given
	 * text direction.
	 * @param textDirection the text direction 
	 * @return the rotation angle to upright/horizontal orientation
	 */
	public static int getToUprightRotation(String textDirection) {
		if (ImLayoutObject.TEXT_DIRECTION_BOTTOM_UP.equals(textDirection))
			return CLOCKWISE_ROTATION;
		else if (ImLayoutObject.TEXT_DIRECTION_TOP_DOWN.equals(textDirection))
			return COUNTER_CLOCKWISE_ROTATION;
		else if (ImLayoutObject.TEXT_DIRECTION_RIGHT_LEFT_UPSIDE_DOWN.equals(textDirection))
			return (CLOCKWISE_ROTATION + CLOCKWISE_ROTATION);
		else return 0;
	}
	
	/**
	 * Transform a point.
	 * @param point the point to transform
	 * @return the transformed point
	 */
	public Point2D.Float transformPoint(Point2D point) {
		float px = ((float) point.getX());
		float py = ((float) point.getY());
		px -= this.fromCenterX;
		py -= this.fromCenterY;
		
		float sdPx;
		float sdPy;
		if (this.turnDegrees == NO_ROTATION) {
			sdPx = px;
			sdPy = py;
		}
		else if (this.turnDegrees == CLOCKWISE_ROTATION) {
			sdPx = -py;
			sdPy = px;
		}
		else if (this.turnDegrees == COUNTER_CLOCKWISE_ROTATION) {
			sdPx = py;
			sdPy = -px;
		}
		else throw new IllegalArgumentException("Can only rotate by 90° (clockwise) or -90° (counter-clockwise), not by " + turnDegrees);
		
		sdPx += this.toCenterX;
		sdPy += this.toCenterY;
		return new Point2D.Float(sdPx, sdPy);
	}
	
	/**
	 * Transform an attribute value indicating an in-page location or direction.
	 * If none of the registered attribute transformers can handle the attribute
	 * with the argument name, this method returns the argument value as it is.
	 * @param object the Image Markup object the attribute belongs to
	 * @param name the name of the attribute
	 * @param value the attribute value to transform
	 * @return the transformed attribute value
	 */
	public Object transformAttributeValue(ImObject object, String name, Object value) {
		for (int t = 0; t < this.attributeTransformers.size(); t++) {
			AttributeTransformer at = ((AttributeTransformer) this.attributeTransformers.get(t));
			if (at.canTransformAttribute(name))
				return at.transformAttributeValue(object, name, value, this);
		}
		for (int t = 0; t < globalAttributeTransformers.size(); t++) {
			AttributeTransformer at = ((AttributeTransformer) globalAttributeTransformers.get(t));
			if (at.canTransformAttribute(name))
				return at.transformAttributeValue(object, name, value, this);
		}
		return value;
	}
	
	/**
	 * Add a transformer for the values of specific attributes to the specific
	 * object transformer. Attribute transformers added this way are consulted
	 * before ones added globally.
	 * @param at the attribute transformer to add
	 */
	public void addAttributeTransformer(AttributeTransformer at) {
		if (at != null)
			this.attributeTransformers.add(at);
	}
	private ArrayList attributeTransformers = new ArrayList();
	
	/**
	 * Globally add a transformer for the values of specific attributes. Any
	 * attribute transformer added via this method must be self-inverse and
	 * should be safe for concurrent use.
	 * @param at the attribute transformer to add
	 */
	public static void addGlobalAttributeTransformer(AttributeTransformer at) {
		if (at != null)
			globalAttributeTransformers.add(at);
	}
	private static ArrayList globalAttributeTransformers = new ArrayList();
	
	/**
	 * Transformer for values of specific attributes value indicating an in-page
	 * location or direction. This interface allows logic handing such
	 * attributes to inject their specific logic into the transformation process.
	 * @author sautter
	 */
	public static abstract class AttributeTransformer {
//		
//		/**
//		 * Create an attribute transformer that inverts the transformations of
//		 * this attribute transformer. This default implementation returns the
//		 * attribute transformer proper. Sub classes that are not self inverse
//		 * need to overwrite this method to provide an inverted transformer.
//		 * @return the inverted attribute transformer
//		 */
//		protected AttributeTransformer invert() {
//			return this;
//		}
		
		/**
		 * Indicate whether or not the transformer can handle the values of a
		 * specific attribute.
		 * @param name the name of the attribute
		 * @return true if the transformer can handle the values of the
		 *        attribute with the argument name
		 */
		public abstract boolean canTransformAttribute(String name);
		
		/**
		 * Transform an attribute value indicating an in-page location or
		 * direction.
		 * @param object the Image Markup object the attribute belongs to
		 * @param name the name of the attribute
		 * @param value the attribute value to transform
		 * @param transformer the object transformer holding the transformation
		 *        parameters
		 * @return the transformed attribute value
		 */
		public abstract Object transformAttributeValue(ImObject object, String name, Object value, ImObjectTransformer transformer);
	}
	
	/** Default transformer for the <code>pageId</code> attribute, transforming
	 * the source page ID into the target page ID of a given object transformer.
	 * To activate this behavior, add this transformer to a specific object
	 * transformer instance, of add it globally. The latter option should be
	 * handled with care, though, as different behavior might be desired in
	 * other client code. */
	public static final AttributeTransformer PAGE_ID_TRANSFORMER = new AttributeTransformer() {
		public boolean canTransformAttribute(String name) {
			return ImLayoutObject.PAGE_ID_ATTRIBUTE.equals(name);
		}
		public Object transformAttributeValue(ImObject object, String name, Object value, ImObjectTransformer transformer) {
			if ((value instanceof Number) && (((Number) value).intValue() == transformer.fromPageId))
				return new Integer(transformer.toPageId);
			else if ((value instanceof CharSequence) && ("" + transformer.fromPageId).equals(value.toString()))
				return ("" + transformer.toPageId);
			else return value;
		}
	};
	
	/** Default transformer for the <code>pageId</code> attribute, transforming
	 * the source page ID into the target page ID of a given object transformer.
	 * To activate this behavior, add this transformer to a specific object
	 * transformer instance, of add it globally. The latter option should be
	 * handled with care, though, as different behavior might be desired in
	 * other client code. */
	public static final AttributeTransformer BOUNDING_BOX_TRANSFORMER = new AttributeTransformer() {
		public boolean canTransformAttribute(String name) {
			return ImLayoutObject.BOUNDING_BOX_ATTRIBUTE.equals(name);
		}
		public Object transformAttributeValue(ImObject object, String name, Object value, ImObjectTransformer transformer) {
			if (value instanceof BoundingBox)
				return transformer.transformBounds((BoundingBox) value);
			else if (value instanceof CharSequence) try {
				BoundingBox bounds = BoundingBox.parse(value.toString());
				return transformer.transformBounds(bounds).toString();
			} catch (IllegalArgumentException iae) {}
			return value;
		}
	};
}
