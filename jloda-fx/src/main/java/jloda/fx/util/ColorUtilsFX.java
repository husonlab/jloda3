/*
 * ColorUtilsFX.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.util;

import javafx.scene.paint.Color;
import jloda.util.AColor;

import java.util.Random;

/**
 * utilities for FX colors
 * Daniel Huson, 10.2022
 */
public class ColorUtilsFX {
	public static boolean isColor(String text) {
		if (text.equals("random"))
			return true;
		{
			try {
				Color.web(text);
				return true;
			} catch (Exception ignored) {
				return false;
			}
		}
	}

	public static Color parseColor(String text) {
		if (text.equals("random")) {
			var random = new Random();
			return new Color(random.nextDouble(), random.nextDouble(), random.nextDouble(), 1);
		} else
			return Color.web(text);
	}

	public static String toStringCSS(Color color) {
		return color.toString().replace("0x", "#");
	}

	public static AColor convert(Color color) {
		return new AColor((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
	}

	public static Color convert(AColor color) {
		return new Color(color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0, color.getAlpha() / 255.0);
	}

	/**
	 * Convert a JavaFX Color to "#RRGGBBAA"
	 */
	public static String toWeb(Color c) {
		int r = (int) Math.round(c.getRed() * 255);
		int g = (int) Math.round(c.getGreen() * 255);
		int b = (int) Math.round(c.getBlue() * 255);
		int a = (int) Math.round(c.getOpacity() * 255);

		return String.format("#%02X%02X%02X%02X", r, g, b, a);
	}

	/**
	 * Parse "#RRGGBB" or "#RRGGBBAA" into a Color
	 */
	public static Color fromWeb(String s) {
		if (s == null || s.isEmpty())
			return Color.BLACK;

		// JavaFX supports both #RRGGBB and #RRGGBBAA
		return Color.web(s);
	}

	public static String getName(Color color) {
		if (Color.ALICEBLUE.equals(color)) return "ALICEBLUE".toLowerCase();
		else if (Color.ANTIQUEWHITE.equals(color)) return "ANTIQUEWHITE".toLowerCase();
		else if (Color.AQUA.equals(color)) return "AQUA".toLowerCase();
		else if (Color.AQUAMARINE.equals(color)) return "AQUAMARINE".toLowerCase();
		else if (Color.AZURE.equals(color)) return "AZURE".toLowerCase();
		else if (Color.BEIGE.equals(color)) return "BEIGE".toLowerCase();
		else if (Color.BISQUE.equals(color)) return "BISQUE".toLowerCase();
		else if (Color.BLACK.equals(color)) return "BLACK".toLowerCase();
		else if (Color.BLANCHEDALMOND.equals(color)) return "BLANCHEDALMOND".toLowerCase();
		else if (Color.BLUE.equals(color)) return "BLUE".toLowerCase();
		else if (Color.BLUEVIOLET.equals(color)) return "BLUEVIOLET".toLowerCase();
		else if (Color.BROWN.equals(color)) return "BROWN".toLowerCase();
		else if (Color.BURLYWOOD.equals(color)) return "BURLYWOOD".toLowerCase();
		else if (Color.CADETBLUE.equals(color)) return "CADETBLUE".toLowerCase();
		else if (Color.CHARTREUSE.equals(color)) return "CHARTREUSE".toLowerCase();
		else if (Color.CHOCOLATE.equals(color)) return "CHOCOLATE".toLowerCase();
		else if (Color.CORAL.equals(color)) return "CORAL".toLowerCase();
		else if (Color.CORNFLOWERBLUE.equals(color)) return "CORNFLOWERBLUE".toLowerCase();
		else if (Color.CORNSILK.equals(color)) return "CORNSILK".toLowerCase();
		else if (Color.CRIMSON.equals(color)) return "CRIMSON".toLowerCase();
		else if (Color.CYAN.equals(color)) return "CYAN".toLowerCase();
		else if (Color.DARKBLUE.equals(color)) return "DARKBLUE".toLowerCase();
		else if (Color.DARKCYAN.equals(color)) return "DARKCYAN".toLowerCase();
		else if (Color.DARKGOLDENROD.equals(color)) return "DARKGOLDENROD".toLowerCase();
		else if (Color.DARKGRAY.equals(color)) return "DARKGRAY".toLowerCase();
		else if (Color.DARKGREEN.equals(color)) return "DARKGREEN".toLowerCase();
		else if (Color.DARKGREY.equals(color)) return "DARKGREY".toLowerCase();
		else if (Color.DARKKHAKI.equals(color)) return "DARKKHAKI".toLowerCase();
		else if (Color.DARKMAGENTA.equals(color)) return "DARKMAGENTA".toLowerCase();
		else if (Color.DARKOLIVEGREEN.equals(color)) return "DARKOLIVEGREEN".toLowerCase();
		else if (Color.DARKORANGE.equals(color)) return "DARKORANGE".toLowerCase();
		else if (Color.DARKORCHID.equals(color)) return "DARKORCHID".toLowerCase();
		else if (Color.DARKRED.equals(color)) return "DARKRED".toLowerCase();
		else if (Color.DARKSALMON.equals(color)) return "DARKSALMON".toLowerCase();
		else if (Color.DARKSEAGREEN.equals(color)) return "DARKSEAGREEN".toLowerCase();
		else if (Color.DARKSLATEBLUE.equals(color)) return "DARKSLATEBLUE".toLowerCase();
		else if (Color.DARKSLATEGRAY.equals(color)) return "DARKSLATEGRAY".toLowerCase();
		else if (Color.DARKSLATEGREY.equals(color)) return "DARKSLATEGREY".toLowerCase();
		else if (Color.DARKTURQUOISE.equals(color)) return "DARKTURQUOISE".toLowerCase();
		else if (Color.DARKVIOLET.equals(color)) return "DARKVIOLET".toLowerCase();
		else if (Color.DEEPPINK.equals(color)) return "DEEPPINK".toLowerCase();
		else if (Color.DEEPSKYBLUE.equals(color)) return "DEEPSKYBLUE".toLowerCase();
		else if (Color.DIMGRAY.equals(color)) return "DIMGRAY".toLowerCase();
		else if (Color.DIMGREY.equals(color)) return "DIMGREY".toLowerCase();
		else if (Color.DODGERBLUE.equals(color)) return "DODGERBLUE".toLowerCase();
		else if (Color.FIREBRICK.equals(color)) return "FIREBRICK".toLowerCase();
		else if (Color.FLORALWHITE.equals(color)) return "FLORALWHITE".toLowerCase();
		else if (Color.FORESTGREEN.equals(color)) return "FORESTGREEN".toLowerCase();
		else if (Color.FUCHSIA.equals(color)) return "FUCHSIA".toLowerCase();
		else if (Color.GAINSBORO.equals(color)) return "GAINSBORO".toLowerCase();
		else if (Color.GHOSTWHITE.equals(color)) return "GHOSTWHITE".toLowerCase();
		else if (Color.GOLD.equals(color)) return "GOLD".toLowerCase();
		else if (Color.GOLDENROD.equals(color)) return "GOLDENROD".toLowerCase();
		else if (Color.GRAY.equals(color)) return "GRAY".toLowerCase();
		else if (Color.GREEN.equals(color)) return "GREEN".toLowerCase();
		else if (Color.GREENYELLOW.equals(color)) return "GREENYELLOW".toLowerCase();
		else if (Color.GREY.equals(color)) return "GREY".toLowerCase();
		else if (Color.HONEYDEW.equals(color)) return "HONEYDEW".toLowerCase();
		else if (Color.HOTPINK.equals(color)) return "HOTPINK".toLowerCase();
		else if (Color.INDIANRED.equals(color)) return "INDIANRED".toLowerCase();
		else if (Color.INDIGO.equals(color)) return "INDIGO".toLowerCase();
		else if (Color.IVORY.equals(color)) return "IVORY".toLowerCase();
		else if (Color.KHAKI.equals(color)) return "KHAKI".toLowerCase();
		else if (Color.LAVENDER.equals(color)) return "LAVENDER".toLowerCase();
		else if (Color.LAVENDERBLUSH.equals(color)) return "LAVENDERBLUSH".toLowerCase();
		else if (Color.LAWNGREEN.equals(color)) return "LAWNGREEN".toLowerCase();
		else if (Color.LEMONCHIFFON.equals(color)) return "LEMONCHIFFON".toLowerCase();
		else if (Color.LIGHTBLUE.equals(color)) return "LIGHTBLUE".toLowerCase();
		else if (Color.LIGHTCORAL.equals(color)) return "LIGHTCORAL".toLowerCase();
		else if (Color.LIGHTCYAN.equals(color)) return "LIGHTCYAN".toLowerCase();
		else if (Color.LIGHTGOLDENRODYELLOW.equals(color)) return "LIGHTGOLDENRODYELLOW".toLowerCase();
		else if (Color.LIGHTGRAY.equals(color)) return "LIGHTGRAY".toLowerCase();
		else if (Color.LIGHTGREEN.equals(color)) return "LIGHTGREEN".toLowerCase();
		else if (Color.LIGHTGREY.equals(color)) return "LIGHTGREY".toLowerCase();
		else if (Color.LIGHTPINK.equals(color)) return "LIGHTPINK".toLowerCase();
		else if (Color.LIGHTSALMON.equals(color)) return "LIGHTSALMON".toLowerCase();
		else if (Color.LIGHTSEAGREEN.equals(color)) return "LIGHTSEAGREEN".toLowerCase();
		else if (Color.LIGHTSKYBLUE.equals(color)) return "LIGHTSKYBLUE".toLowerCase();
		else if (Color.LIGHTSLATEGRAY.equals(color)) return "LIGHTSLATEGRAY".toLowerCase();
		else if (Color.LIGHTSLATEGREY.equals(color)) return "LIGHTSLATEGREY".toLowerCase();
		else if (Color.LIGHTSTEELBLUE.equals(color)) return "LIGHTSTEELBLUE".toLowerCase();
		else if (Color.LIGHTYELLOW.equals(color)) return "LIGHTYELLOW".toLowerCase();
		else if (Color.LIME.equals(color)) return "LIME".toLowerCase();
		else if (Color.LIMEGREEN.equals(color)) return "LIMEGREEN".toLowerCase();
		else if (Color.LINEN.equals(color)) return "LINEN".toLowerCase();
		else if (Color.MAGENTA.equals(color)) return "MAGENTA".toLowerCase();
		else if (Color.MAROON.equals(color)) return "MAROON".toLowerCase();
		else if (Color.MEDIUMAQUAMARINE.equals(color)) return "MEDIUMAQUAMARINE".toLowerCase();
		else if (Color.MEDIUMBLUE.equals(color)) return "MEDIUMBLUE".toLowerCase();
		else if (Color.MEDIUMORCHID.equals(color)) return "MEDIUMORCHID".toLowerCase();
		else if (Color.MEDIUMPURPLE.equals(color)) return "MEDIUMPURPLE".toLowerCase();
		else if (Color.MEDIUMSEAGREEN.equals(color)) return "MEDIUMSEAGREEN".toLowerCase();
		else if (Color.MEDIUMSLATEBLUE.equals(color)) return "MEDIUMSLATEBLUE".toLowerCase();
		else if (Color.MEDIUMSPRINGGREEN.equals(color)) return "MEDIUMSPRINGGREEN".toLowerCase();
		else if (Color.MEDIUMTURQUOISE.equals(color)) return "MEDIUMTURQUOISE".toLowerCase();
		else if (Color.MEDIUMVIOLETRED.equals(color)) return "MEDIUMVIOLETRED".toLowerCase();
		else if (Color.MIDNIGHTBLUE.equals(color)) return "MIDNIGHTBLUE".toLowerCase();
		else if (Color.MINTCREAM.equals(color)) return "MINTCREAM".toLowerCase();
		else if (Color.MISTYROSE.equals(color)) return "MISTYROSE".toLowerCase();
		else if (Color.MOCCASIN.equals(color)) return "MOCCASIN".toLowerCase();
		else if (Color.NAVAJOWHITE.equals(color)) return "NAVAJOWHITE".toLowerCase();
		else if (Color.NAVY.equals(color)) return "NAVY".toLowerCase();
		else if (Color.OLDLACE.equals(color)) return "OLDLACE".toLowerCase();
		else if (Color.OLIVE.equals(color)) return "OLIVE".toLowerCase();
		else if (Color.OLIVEDRAB.equals(color)) return "OLIVEDRAB".toLowerCase();
		else if (Color.ORANGE.equals(color)) return "ORANGE".toLowerCase();
		else if (Color.ORANGERED.equals(color)) return "ORANGERED".toLowerCase();
		else if (Color.ORCHID.equals(color)) return "ORCHID".toLowerCase();
		else if (Color.PALEGOLDENROD.equals(color)) return "PALEGOLDENROD".toLowerCase();
		else if (Color.PALEGREEN.equals(color)) return "PALEGREEN".toLowerCase();
		else if (Color.PALETURQUOISE.equals(color)) return "PALETURQUOISE".toLowerCase();
		else if (Color.PALEVIOLETRED.equals(color)) return "PALEVIOLETRED".toLowerCase();
		else if (Color.PAPAYAWHIP.equals(color)) return "PAPAYAWHIP".toLowerCase();
		else if (Color.PEACHPUFF.equals(color)) return "PEACHPUFF".toLowerCase();
		else if (Color.PERU.equals(color)) return "PERU".toLowerCase();
		else if (Color.PINK.equals(color)) return "PINK".toLowerCase();
		else if (Color.PLUM.equals(color)) return "PLUM".toLowerCase();
		else if (Color.POWDERBLUE.equals(color)) return "POWDERBLUE".toLowerCase();
		else if (Color.PURPLE.equals(color)) return "PURPLE".toLowerCase();
		else if (Color.RED.equals(color)) return "RED".toLowerCase();
		else if (Color.ROSYBROWN.equals(color)) return "ROSYBROWN".toLowerCase();
		else if (Color.ROYALBLUE.equals(color)) return "ROYALBLUE".toLowerCase();
		else if (Color.SADDLEBROWN.equals(color)) return "SADDLEBROWN".toLowerCase();
		else if (Color.SALMON.equals(color)) return "SALMON".toLowerCase();
		else if (Color.SANDYBROWN.equals(color)) return "SANDYBROWN".toLowerCase();
		else if (Color.SEAGREEN.equals(color)) return "SEAGREEN".toLowerCase();
		else if (Color.SEASHELL.equals(color)) return "SEASHELL".toLowerCase();
		else if (Color.SIENNA.equals(color)) return "SIENNA".toLowerCase();
		else if (Color.SILVER.equals(color)) return "SILVER".toLowerCase();
		else if (Color.SKYBLUE.equals(color)) return "SKYBLUE".toLowerCase();
		else if (Color.SLATEBLUE.equals(color)) return "SLATEBLUE".toLowerCase();
		else if (Color.SLATEGRAY.equals(color)) return "SLATEGRAY".toLowerCase();
		else if (Color.SLATEGREY.equals(color)) return "SLATEGREY".toLowerCase();
		else if (Color.SNOW.equals(color)) return "SNOW".toLowerCase();
		else if (Color.SPRINGGREEN.equals(color)) return "SPRINGGREEN".toLowerCase();
		else if (Color.STEELBLUE.equals(color)) return "STEELBLUE".toLowerCase();
		else if (Color.TAN.equals(color)) return "TAN".toLowerCase();
		else if (Color.TEAL.equals(color)) return "TEAL".toLowerCase();
		else if (Color.THISTLE.equals(color)) return "THISTLE".toLowerCase();
		else if (Color.TOMATO.equals(color)) return "TOMATO".toLowerCase();
		else if (Color.TURQUOISE.equals(color)) return "TURQUOISE".toLowerCase();
		else if (Color.VIOLET.equals(color)) return "VIOLET".toLowerCase();
		else if (Color.WHEAT.equals(color)) return "WHEAT".toLowerCase();
		else if (Color.WHITE.equals(color)) return "WHITE".toLowerCase();
		else if (Color.WHITESMOKE.equals(color)) return "WHITESMOKE".toLowerCase();
		else if (Color.YELLOW.equals(color)) return "YELLOW".toLowerCase();
		else if (Color.YELLOWGREEN.equals(color)) return "YELLOWGREEN".toLowerCase();
		else return color.toString();
	}
}
