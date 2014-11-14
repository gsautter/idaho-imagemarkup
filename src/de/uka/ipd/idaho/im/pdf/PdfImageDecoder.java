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
		else {
			System.out.println("OcrEngin: unknown OS name: " + osName);
			return null;
		}
	}
	
	private File imPath;
	
	/** Constructor
	 * @param tessPath the folder to work in
	 */
	public PdfImageDecoder(File imPath) throws IOException {
		this.imPath = imPath;
		if (!this.imPath.exists())
			this.imPath.mkdirs();
		if (!this.install(getConvertCommand()))
			throw new IOException("PdfImageDecoder: binary not found");
	}
	
	private boolean install(String fileName) {
		System.out.println("PDF Image Decoder: installing file '" + fileName + "'");
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
		try {
			String[] command = {
					(this.imPath.getAbsolutePath() + "/" + getConvertCommand()),
					(imageFormat + ":-"),
					("png:-"),
			};
			Process im = Runtime.getRuntime().exec(command, null, imPath.getAbsoluteFile());
			OutputStream imIn = im.getOutputStream();
			imIn.write(imageBytes);
			imIn.flush();
			imIn.close();
			BufferedImage image = ImageIO.read(im.getInputStream());
			im.waitFor();
			return image;
		}
		catch (InterruptedException ie) {
			return null;
		}
	}
}