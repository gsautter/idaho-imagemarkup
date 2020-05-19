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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helper class for installing and updating binary files for tools called by
 * Image Markup facilities for better performance and support of a wider range
 * of file formats.
 * 
 * @author sautter
 */
public class BinaryToolInstaller {
	
	/**
	 * Install a binary file in a designated destination folder. This installer
	 * routine both checks for the presence of the destination file and for its
	 * being up to date. If the destination file exists but differs in size
	 * from the one currently available from the class path, this routine will
	 * overwrite the previously existing version with the current one.
	 * @param fileName the name to store the file under
	 * @param destFolder the folder to store the file in
	 * @param binByteLoader the class loader to obtain the binary from
	 * @param binPathPrefix the path prefix of the binary in the class loader
	 * @return true if the binary was installed successfully or already existed
	 * @throws IOException
	 */
	public static boolean install(String fileName, File destFolder, ClassLoader binLoader, String binPathPrefix) {
		System.out.println("Installing binary file '" + fileName + "' to " + destFolder.getAbsolutePath());
		if (fileName == null) {
			System.out.println(" ==> source name not found");
			return false;
		}
		
		//	buffer (current version of) binary
		ByteArrayOutputStream binBuffer = null;
		InputStream binIn = binLoader.getResourceAsStream(binPathPrefix + (binPathPrefix.endsWith("/") ? "" : "/") + fileName);
		Exception binLoadError = null;
		if (binIn != null) try {
			binBuffer = new ByteArrayOutputStream();
			binIn = new BufferedInputStream(binIn);
			byte[] loadBuffer = new byte[2048];
			for (int r; (r = binIn.read(loadBuffer, 0, loadBuffer.length)) != -1;)
				binBuffer.write(loadBuffer, 0, r);
			binBuffer.flush();
			binBuffer.close();
			binIn.close();
		}
		catch (Exception e) {
			binLoadError = e;
		}
		
		//	check previously existing file
		File binFile = new File(destFolder, fileName);
		if (binFile.exists()) {
			if (binIn == null) {
				System.out.println(" ==> source not found, using existing file");
				return true;
			}
			else if (Math.abs(binFile.length() - binBuffer.size()) < 16) {
				System.out.println(" ==> already installed (file sizes " + binFile.length() + " bytes on disk vs. " + binBuffer.size() + " bytes loaded)");
				return true;
			}
			else {
				long binFileSize = binFile.length(); // cannot recover this after deleting ...
				binFile.delete();
				binFile = new File(destFolder, fileName);
				System.out.println(" ==> deleted outdated file (sized " + binFileSize + " bytes vs. " + binBuffer.size() + " bytes loaded)");
			}
		}
		else {
			if (binIn == null) {
				System.out.println(" ==> source not found");
				return false;
			}
			else if (binLoadError != null) {
				System.out.println(" ==> could not load source: " + binLoadError.getMessage());
				binLoadError.printStackTrace(System.out);
				return false;
			}
		}
		
		//	store current version in destination folder
		try {
			binFile.getParentFile().mkdirs();
			binIn = new ByteArrayInputStream(binBuffer.toByteArray());
			OutputStream binOut = new BufferedOutputStream(new FileOutputStream(binFile));
			byte[] outBuffer = new byte[2048];
			for (int r; (r = binIn.read(outBuffer, 0, outBuffer.length)) != -1;)
				binOut.write(outBuffer, 0, r);
			binOut.flush();
			binOut.close();
			binIn.close();
			System.out.println(" ==> installed successfully");
			
			if (fileName.endsWith("-linux") || fileName.endsWith("-mac") || fileName.startsWith("macos")) {
//				Runtime.getRuntime().exec(("chmod -R 777 " + destFolder.getAbsolutePath()), new String[0], destFolder);
				Runtime.getRuntime().exec(("chmod -R 777 " + binFile.getName()), null, binFile.getParentFile());
//				ProcessBuilder setPermissions = new ProcessBuilder(("chmod -R 777 " + binFile.getName()).split("\\s+"));
//				setPermissions.directory(binFile.getParentFile());
//				setPermissions.start();
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
}
