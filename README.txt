Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the Universitaet Karlsruhe (TH) nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


This project is provides GAMTA image markup facilities, the foundations for image
based document markup, including in memory data representation, facilities for storage,
loading, and data transfer, and adapters to the GAMTA data model to facilitate seamless
application of respective text analysis components. It is not an application proper,
but rather provides additional (shared) base libraries for multiple other projects.
The Ant build script produces five JAR files in the "dist" folder, namely:

- ImageMarkup.jar: The image markup data model, GAMTA wrapper, IO utilities, and
                   display and editing widgets

- ImageMarkupOCR.jar: Java wrapper for Stream-Tesseract OCR engine

- ImageMarkupOCR.bin.jar: The Stream-Tesseract OCR engine binaries and language
                          files (separated from Java code for update granularity)

- ImageMarkupPDF.jar: PDF decoder, dependent on IcePDF

- ImageMarkupPDF.bin.jar: ImageMagick binaries used for image format conversion
                          (separated from Java code for update granularity)

All five JAR files generated by Ant include both the Java sources and the class files,
as well as other resources required for the code to work.


This project depends icepdf-core.jar (by IceSoft, http://www.icesoft.org/java/projects/ICEpdf/overview.jsf,
used under the terms of the Mozilla Public License) to build (included in the "lib" folder for convenience).

Further, this project requires the JAR files build by the Ant scripts in the
idaho-core (http://code.google.com/p/idaho-core/) and idaho-extensions
(http://code.google.com/p/idaho-extensions/) projects, as well as the JAR files
referenced from there.
See http://code.google.com/p/idaho-core/source/browse/README.txt and
http://code.google.com/p/idaho-extensions/source/browse/README.txt for the
latter. You can either check out idaho-core and idaho-extensions as projects into the
same workspace and build them first, or include the JAR files they generate in the
"lib" folder.