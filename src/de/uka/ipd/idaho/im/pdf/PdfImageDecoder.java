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
package de.uka.ipd.idaho.im.pdf;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;

/**
 * Wrapper for the Image Magick <b>convert</b> tool. The Image Magick suite
 * has been built ImageMagick Studio LLC since 1999.
 * 
 * @author sautter
 */
public class PdfImageDecoder {

	private static String getConvertCommand() {
		String osName = System.getProperty("os.name");
		if (osName.matches("Win.*"))
			return "convert-windows.exe";
		else if (osName.matches(".*Linux.*"))
			return "convert-linux";
		else if (osName.matches("Mac.*"))
			return "convert-linux";
		else {
			System.out.println("PdfImageDecoder: unknown OS name: " + osName);
			return null;
		}
	}

	private static String getConvertSystemCommand() {
		String osName = System.getProperty("os.name");
		if (osName.matches(".*Linux.*"))
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
		System.out.println("PDF Image Decoder: installing file '" + fileName + "'");
		if (fileName == null) {
			System.out.println(" ==> source name not found");
			return false;
		}
		
		File file = new File(this.imPath, fileName);
		if (file.exists()) {
			System.out.println(" ==> already installed");
			return true;
		}
		
		InputStream fileIn = this.getClass().getClassLoader().getResourceAsStream("bin/ImageMagick/" + fileName);
		if (fileIn == null) {
			System.out.println(" ==> source not found");
			return false;
		}
		fileIn = new BufferedInputStream(fileIn);
		
		try {
			file.getParentFile().mkdirs();
			OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(file));
			byte[] fileBuffer = new byte[2048];
			for (int r; (r = fileIn.read(fileBuffer, 0, fileBuffer.length)) != -1;)
				fileOut.write(fileBuffer, 0, r);
			fileOut.flush();
			fileOut.close();
			fileIn.close();
			System.out.println(" ==> installed successfully");
			
			if (fileName.endsWith("-linux") || fileName.endsWith("-mac")) {
				Runtime.getRuntime().exec(("chmod -R 777 " + this.imPath.getAbsolutePath()), new String[0], this.imPath);
				System.out.println(" ==> execution permissions obtained successfully");
			}
			
			return true;
		}
		catch (IOException ioe) {
			System.out.println(" ==> could not install file '" + fileName + "'");
			ioe.printStackTrace(System.out);
			return false;
		}
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
		return this.decodeImage(imageBytes, imageFormat, null);
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
		return this.decodeImage(imageBytes, imageFormat, colorSpace, null);
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
		try {
			ArrayList command = new ArrayList();
			if (this.useSystemCommand)
				command.add(getConvertSystemCommand());
			else command.add(this.imPath.getAbsolutePath() + "/" + getConvertCommand());
			command.add(imageFormat + ":-");
			if ((colorSpace != null) && colorSpace.toUpperCase().endsWith("CMYK")) {
				command.add("-negate");
				command.add("-profile");
				command.add(this.imPath.getAbsolutePath() + "/ISOcoated_v2_300_eci.icc");
			}
			else if ((colorSpace != null) && colorSpace.toUpperCase().startsWith("ICCB") && (altColorSpace != null) && altColorSpace.toUpperCase().endsWith("CMYK")) {
				command.add("-negate");
				command.add("-profile");
				command.add(this.imPath.getAbsolutePath() + "/ISOcoated_v2_300_eci.icc");
			}
			else if ("SeparationBlack".equals(colorSpace) && ("DeviceRGB".equals(altColorSpace) || "DeviceGray".equals(altColorSpace)))
				command.add("-negate");
			command.add("png:-");
			System.out.println("PdfImageDecoder: command is " + command);
			Process im = Runtime.getRuntime().exec(((String[]) command.toArray(new String[command.size()])), null, imPath.getAbsoluteFile());
//			System.out.println(" - process created");
			OutputStream imIn = im.getOutputStream();
//			System.out.println(" - got output stream");
			imIn.write(imageBytes);
			imIn.flush();
			imIn.close();
//			System.out.println(" - image data sent");
			BufferedImage image = ImageIO.read(im.getInputStream());
			if (image == null) {
				File imageData = new File(this.imPath, ("ImageData" + System.currentTimeMillis() + "." + imageFormat));
				FileOutputStream imageDataOut = new FileOutputStream(imageData);
				imageDataOut.write(imageBytes);
				imageDataOut.flush();
				imageDataOut.close();
//				System.out.println(" - could not read back image, data in " + imageData.getAbsolutePath());
			}
//			else System.out.println(" - image read back");
			im.waitFor();
//			System.out.println(" - process terminated");
			return image;
		}
		catch (InterruptedException ie) {
//			System.out.println("Image Magic got interrupted");
			return null;
		}
	}
}