/*
 * SaveToPDF.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jloda.fx.print;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.fop.svg.PDFDocumentGraphics2D;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.file.Files;

/**
 * Save a JavaFX node to PDF by first exporting it as SVG and then rendering
 * the SVG into a PDF Graphics2D using Batik and Apache FOP.
 * <p>
 * This avoids maintaining a second, incomplete PDF drawing backend. The quality
 * of the resulting PDF is therefore determined primarily by SaveToSVG.
 * <p>
 * Daniel Huson, 6.2026
 */
public class SaveToPDF {
	static {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	/**
	 * Draws the given root node to a file in PDF format.
	 *
	 * @param root the root node to be saved
	 * @param file the output PDF file
	 * @throws IOException failed
	 */
	public static void apply(Node root, File file) throws IOException {
		apply(root, root.getBoundsInLocal(), file);
	}

	/**
	 * Draws the given root node to a file in PDF format, using the supplied bounds.
	 *
	 * @param root   the root node to be saved
	 * @param bounds the bounds to use for the SVG/PDF page
	 * @param file   the output PDF file
	 * @throws IOException failed
	 */
	public static void apply(Node root, Bounds bounds, File file) throws IOException {
		var tmpSvg = Files.createTempFile("jloda-export-", ".svg");
		try {
			//System.err.println("Using SaveToPDF_deprecated-SVG");
			SaveToSVG.apply(root, bounds, tmpSvg.toFile());
			convert(tmpSvg.toFile(), file);
		} finally {
			Files.deleteIfExists(tmpSvg);
		}
	}

	/**
	 * Converts an SVG file to a PDF file using Batik's rendering engine and
	 * FOP's PDFDocumentGraphics2D.
	 *
	 * @param svgFile the input SVG file
	 * @param pdfFile the output PDF file
	 * @throws IOException failed
	 */
	public static void convert(File svgFile, File pdfFile) throws IOException {
		if (pdfFile.exists())
			Files.delete(pdfFile.toPath());

		try (var in = new FileInputStream(svgFile);
			 var out = new FileOutputStream(pdfFile)) {
			convert(in, svgFile.toURI().toString(), out);
		}
	}

	/**
	 * Converts SVG data to PDF using Batik's rendering engine and
	 * FOP's PDFDocumentGraphics2D.
	 * <p>
	 * The systemId should be the SVG file URI, if available. It is used by Batik
	 * to resolve relative references inside the SVG.
	 *
	 * @param svgInput  input stream containing SVG data
	 * @param systemId  SVG system id, usually svgFile.toURI().toString(); may be null
	 * @param pdfOutput output stream for PDF data
	 * @throws IOException failed
	 */
	public static void convert(InputStream svgInput, String systemId, OutputStream pdfOutput) throws IOException {
		var parser = XMLResourceDescriptor.getXMLParserClassName();
		var factory = new SAXSVGDocumentFactory(parser);

		var document = factory.createDocument(systemId, svgInput);
		var svgElement = document.getDocumentElement();

		var pageSize = getPageSize(svgElement);
		int pageWidth = Math.max(1, (int) Math.ceil(pageSize.width));
		int pageHeight = Math.max(1, (int) Math.ceil(pageSize.height));

		var userAgent = new UserAgentAdapter();
		var loader = new DocumentLoader(userAgent);
		var bridgeContext = new BridgeContext(userAgent, loader);
		bridgeContext.setDynamicState(BridgeContext.STATIC);

		try {
			var graphicsNode = new GVTBuilder().build(bridgeContext, document);

			var pdfGraphics = new PDFDocumentGraphics2D(false);
			pdfGraphics.setGraphicContext(new GraphicContext());
			pdfGraphics.setupDocument(pdfOutput, pageWidth, pageHeight);

			graphicsNode.paint(pdfGraphics);
			pdfGraphics.finish();
			pdfOutput.flush();
		} finally {
			bridgeContext.dispose();
		}
	}

	/**
	 * Determine the PDF page size from the SVG root element.
	 * <p>
	 * SaveToSVG normally writes numeric width and height attributes. If these are
	 * absent, fall back to the width and height components of the viewBox.
	 */
	private static PageSize getPageSize(Element svgElement) throws IOException {
		var width = parseSvgLength(svgElement.getAttribute("width"));
		var height = parseSvgLength(svgElement.getAttribute("height"));

		if (!(width > 0) || !(height > 0)) {
			var viewBox = svgElement.getAttribute("viewBox");
			if (viewBox != null && !viewBox.isBlank()) {
				var tokens = viewBox.trim().split("[\\s,]+");
				if (tokens.length == 4) {
					if (!(width > 0))
						width = Double.parseDouble(tokens[2]);
					if (!(height > 0))
						height = Double.parseDouble(tokens[3]);
				}
			}
		}

		if (!(width > 0) || !(height > 0))
			throw new IOException("Could not determine SVG width and height");

		return new PageSize(width, height);
	}

	/**
	 * Parses a simple SVG length. This intentionally handles the common export
	 * forms used by SaveToSVG: plain numbers, px, pt, in, cm and mm.
	 */
	private static double parseSvgLength(String value) {
		if (value == null)
			return Double.NaN;

		value = value.trim();
		if (value.isEmpty())
			return Double.NaN;

		try {
			if (value.endsWith("px"))
				return Double.parseDouble(value.substring(0, value.length() - 2).trim());
			else if (value.endsWith("pt"))
				return Double.parseDouble(value.substring(0, value.length() - 2).trim());
			else if (value.endsWith("in"))
				return 72.0 * Double.parseDouble(value.substring(0, value.length() - 2).trim());
			else if (value.endsWith("cm"))
				return 72.0 * Double.parseDouble(value.substring(0, value.length() - 2).trim()) / 2.54;
			else if (value.endsWith("mm"))
				return 72.0 * Double.parseDouble(value.substring(0, value.length() - 2).trim()) / 25.4;
			else
				return Double.parseDouble(value);
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	private record PageSize(double width, double height) {
	}
}
