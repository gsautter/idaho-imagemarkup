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
package de.uka.ipd.idaho.im.pdf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

import de.uka.ipd.idaho.im.util.BinaryToolInstaller;

/**
 * Wrapper for the Image Magick <b>convert</b> tool. The Image Magick suite
 * has been built ImageMagick Studio LLC since 1999.
 * 
 * @author sautter
 */
public class PdfImageDecoder {
	private static final boolean DEBUG = false;
	
	private static String getConvertCommand() {
		String osName = System.getProperty("os.name");
		if (osName.matches("Win.*"))
			return "convert-windows.exe";
		else if (osName.matches(".*Linux.*"))
			return "convert-linux";
//		else if (osName.matches("Mac.*"))
//			return "convert-linux";
		else if (osName.matches("Mac.*"))
			return "macosBin/convert";
		else {
			System.out.println("PdfImageDecoder: unknown OS name: " + osName);
			return null;
		}
	}
	
	private static String getConvertSystemCommand() {
		String osName = System.getProperty("os.name");
		if (osName.matches(".*Linux.*"))
			return "convert";
		else if (osName.matches("Mac.*"))
			return "convert";
		else {
			System.out.println("PdfImageDecoder: system command unknown for OS name: " + osName);
			return null;
		}
	}
	
	private File imPath;
	private boolean useSystemCommand = false;;
	
	/** Constructor
	 * @param tessPath the folder to work in
	 */
	public PdfImageDecoder(File imPath) throws IOException {
		this.imPath = imPath;
		if (!this.imPath.exists())
			this.imPath.mkdirs();
		if (!this.install(getConvertCommand())) {
			if (getConvertSystemCommand() == null)
				throw new IOException("PdfImageDecoder: binary not found");
			else this.useSystemCommand = true;
		}
		this.install("ISOcoated_v2_300_eci.icc"); // color profile for CMYK -> RGB conversion
	}
	
	private boolean install(String fileName) {
		System.out.print("PDF Image Decoder: ");
		if (fileName.startsWith("macosBin/")) {
			BinaryToolInstaller.install("macosLib/libMagickCore-7.Q16HDRI.6.dylib", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickCore-7.Q16HDRI.a", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickCore-7.Q16HDRI.dylib", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickCore-7.Q16HDRI.la", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickWand-7.Q16HDRI.6.dylib", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickWand-7.Q16HDRI.a", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickWand-7.Q16HDRI.dylib", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
			BinaryToolInstaller.install("macosLib/libMagickWand-7.Q16HDRI.la", this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
		}
		return BinaryToolInstaller.install(fileName, this.imPath, this.getClass().getClassLoader(), "bin/ImageMagick/");
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat) throws IOException {
		return this.decodeImage(imageBytes, imageFormat, null, null, false);
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @param decodeInverted does the Decode parameter indicate an inversion?
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat, boolean decodeInverted) throws IOException {
		return this.decodeImage(imageBytes, imageFormat, null, null, decodeInverted);
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @param colorSpace the name of the color space used in the input data
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat, String colorSpace) throws IOException {
		return this.decodeImage(imageBytes, imageFormat, colorSpace, null, false);
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @param colorSpace the name of the color space used in the input data
	 * @param decodeInverted does the Decode parameter indicate an inversion?
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat, String colorSpace, boolean decodeInverted) throws IOException {
		return this.decodeImage(imageBytes, imageFormat, colorSpace, null, decodeInverted);
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @param colorSpace the name of the color space used in the input data
	 * @param altColorSpace the name of the alternative color space specified in the argument color space definition
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat, String colorSpace, String altColorSpace) throws IOException {
		return this.decodeImage(imageBytes, imageFormat, colorSpace, altColorSpace, false);
	}
	
	/**
	 * Decode an image given as an array of bytes with the help of the Image
	 * Magick <b>convert</b> tool.
	 * @param imageBytes the bytes representing the image
	 * @param imageFormat the format the image data is in
	 * @param colorSpace the name of the color space used in the input data
	 * @param altColorSpace the name of the alternative color space specified in the argument color space definition
	 * @param decodeInverted does the Decode parameter indicate an inversion?
	 * @return the decoded image
	 * @throws IOException
	 */
	public BufferedImage decodeImage(byte[] imageBytes, String imageFormat, String colorSpace, String altColorSpace, boolean decodeInverted) throws IOException {
		try {
			ArrayList command = new ArrayList();
			HashMap environment = new HashMap();
			if (this.useSystemCommand)
				command.add(getConvertSystemCommand());
			else {
				String cc = getConvertCommand();
				command.add(this.imPath.getAbsolutePath() + "/" + cc);
				if (cc.endsWith("-linux"))
					command.add("convert");
				else if (cc.startsWith("macosBin/"))
					environment.put("DYLD_LIBRARY_PATH", "./macosLib/");
			}
			command.add(imageFormat + ":-");
			if ((colorSpace != null) && colorSpace.toUpperCase().endsWith("CMYK")) {
				if (!decodeInverted)
					command.add("-negate");
				command.add("-profile");
				command.add(this.imPath.getAbsolutePath() + "/ISOcoated_v2_300_eci.icc");
//				command.add(this.imPath.getAbsolutePath() + "/USWebCoatedSWOP.icc");
			}
			else if ((colorSpace != null) && colorSpace.toUpperCase().startsWith("ICCB") && (altColorSpace != null) && altColorSpace.toUpperCase().endsWith("CMYK")) {
				if (!decodeInverted)
					command.add("-negate");
				command.add("-profile");
				command.add(this.imPath.getAbsolutePath() + "/ISOcoated_v2_300_eci.icc");
//				command.add(this.imPath.getAbsolutePath() + "/USWebCoatedSWOP.icc");
			}
			else if (!decodeInverted && "SeparationBlack".equals(colorSpace) && ("DeviceRGB".equals(altColorSpace) || "DeviceGray".equals(altColorSpace)))
				command.add("-negate");
			else if (!decodeInverted && "SeparationBlack".equals(colorSpace) && "DeviceCMYK".equals(altColorSpace))
				command.add("-negate");
			else if (!decodeInverted && "SeparationBlack".equals(colorSpace) && (altColorSpace != null) && altColorSpace.toUpperCase().startsWith("ICCB"))
				command.add("-negate");
			else if (!decodeInverted && "DeviceN".equals(colorSpace) && ("DeviceRGB".equals(altColorSpace) || "DeviceGray".equals(altColorSpace)))
				command.add("-negate");
			else if (!decodeInverted && "DeviceN".equals(colorSpace) && "DeviceCMYK".equals(altColorSpace))
				command.add("-negate");
			//	TODO figure out if we have to negate if only decodeInverted is set ...
			command.add("png:-");
			System.out.println("PdfImageDecoder: command is " + command);
//			Process imProcess = Runtime.getRuntime().exec(((String[]) command.toArray(new String[command.size()])), environment, imPath.getAbsoluteFile());
			ProcessBuilder imBuilder = new ProcessBuilder(command);
			imBuilder.environment().putAll(environment);
			imBuilder.directory(this.imPath.getAbsoluteFile());
			
			Process imProcess = imBuilder.start();
			if (DEBUG) System.out.println(" - process created");
			try {
				OutputStream toIm = imProcess.getOutputStream();
				System.out.println(" - got output stream");
				toIm.write(imageBytes);
				toIm.flush();
				toIm.close();
			}
			catch (IOException ioe) {
				InputStream imIn = imProcess.getInputStream();
				for (int r; (r = imIn.read()) != -1;)
					System.out.print((char) r);
				InputStream imErr = imProcess.getErrorStream();
				for (int r; (r = imErr.read()) != -1;)
					System.err.print((char) r);
				throw ioe;
			}
			if (DEBUG) System.out.println(" - image data sent");
			InputStream fromIm = imProcess.getInputStream();
			BufferedImage image = ImageIO.read(fromIm);
			fromIm.close();
			if (image == null) {
				File imageData = new File(this.imPath, ("ImageData" + System.currentTimeMillis() + "." + imageFormat));
				FileOutputStream imageDataOut = new FileOutputStream(imageData);
				imageDataOut.write(imageBytes);
				imageDataOut.flush();
				imageDataOut.close();
				if (DEBUG) System.out.println(" - could not read back image, data in " + imageData.getAbsolutePath());
			}
			else if (DEBUG) System.out.println(" - image read back");
			
			imProcess.waitFor();
			if (DEBUG) System.out.println(" - process terminated");
			if (command.contains("-negate")) {
				for (int x = 0; x < image.getWidth(); x++)
					for (int y = 0; y < image.getHeight(); y++) {
						int rgb = image.getRGB(x, y);
						int alpha = ((rgb >>> 24) & 0xFF);
						if (alpha == 255)
							continue;
						int red = ((rgb >>> 16) & 0xFF);
						int green = ((rgb >>> 8) & 0xFF);
						int blue = ((rgb >>> 0) & 0xFF);
						red = (((red * alpha) / 255) + ((0 * (255 - alpha)) / 255));
						green = (((green * alpha) / 255) + ((0 * (255 - alpha)) / 255));
						blue = (((blue * alpha) / 255) + ((0 * (255 - alpha)) / 255));
						rgb = ((0xFF << 24) | (red << 16) | (green << 8) | (blue << 0));
						image.setRGB(x, y, rgb);
					}
				if (DEBUG) System.out.println(" - transparent background blended onto black");
			}
			return image;
		}
		catch (InterruptedException ie) {
			System.out.println("Image Magic got interrupted");
			return null;
		}
	}
}