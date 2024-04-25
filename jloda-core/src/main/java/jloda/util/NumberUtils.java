/*
 * NumberUtils.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.util;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class NumberUtils {
	/**
	 * Round to a given number of significant figures
	 *
	 * @param num    double
	 * @param digits number of digits
	 * @return double
	 */
	public static double roundSigFig(double num, int digits) {
		if (num == 0) {
			return 0;
		}

		final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
		final int power = digits - (int) d;

		final double magnitude = Math.pow(10, power);
		final long shifted = Math.round(num * magnitude);
		return shifted / magnitude;
	}

	/**
	 * returns the sign of x
	 *
	 * @return sign of x
	 */
	public static int sign(double x) {
		if (x > 0)
			return 1;
		else if (x < 0)
			return -1;
		else
			return 0;
	}

	/**
	 * gets the min value of an array
	 *
	 * @return min
	 */
	public static int min(int... array) {
		var m = Integer.MAX_VALUE;
		for (var x : array) {
			if (x < m)
				m = x;

		}
		return m;
	}

	/**
	 * gets the max value of an array
	 *
	 * @return max
	 */
	public static int max(int... array) {
		var m = Integer.MIN_VALUE;
		for (var x : array) {
			if (x > m)
				m = x;
		}
		return m;
	}

	/**
	 * gets the max value of an array
	 */
	public static int max(Iterable<Integer> list) {
		var m = Integer.MIN_VALUE;
		for (var x : list) {
			if (x != null && x > m)
				m = x;
		}
		return m;
	}

	/**
	 * returns true, if string can be parsed as int
	 *
	 * @return true, if int
	 */
	public static boolean isInteger(String text) {
		return isInteger(text, text.startsWith("x") ? 16 : 10);
	}

	/**
	 * returns true, if string can be parsed as int
	 *
	 * @return true, if int
	 */
	public static boolean isInteger(String text, int base) {
		try {
			Integer.parseInt(text, base);
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	/**
	 * returns true, if string can be parsed as a boolean
	 *
	 * @return true, if boolean
	 */
	public static boolean isBoolean(String text) {
		return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false");
	}

	/**
	 * returns true, if string can be parsed as boolean
	 *
	 * @return true, if boolean
	 */
	public static boolean parseBoolean(String text) {
		text = text.trim();
		return text.length() >= 4 && text.substring(0, 4).toLowerCase().startsWith("true");
	}

	/**
	 * returns true, if string can be parsed as long
	 *
	 * @return true, if long
	 */
	public static boolean isLong(String text) {
		try {
			Long.parseLong(text);
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	/**
	 * returns true, if string can be parsed as float
	 *
	 * @return true, if float
	 */
	public static boolean isFloat(String text) {
		try {
			Float.parseFloat(text);
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	/**
	 * returns true, if string can be parsed as double
	 *
	 * @return true, if double
	 */
	public static boolean isDouble(String text) {
		try {
			Double.parseDouble(text);
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	/**
	 * attempts to parse the string as an integer, skipping leading chars and trailing characters, if necessary.
	 * Returns 0, if no number found
	 *
	 * @return value or 0
	 */
	public static int parseInt(String string) {
		try {
			if (string != null) {
				var start = 0;
				while (start < string.length()) {
					var ch = string.charAt(start);
					if (Character.isDigit(ch) || ch == '-')
						break;
					start++;
				}
				if (start < string.length()) {
					var finish = start + 1;
					while (finish < string.length() && Character.isDigit(string.charAt(finish)))
						finish++;
					if (start < finish)
						return Integer.parseInt(string.substring(start, finish));
				}
			}
		} catch (Exception ex) {
			if (string.equalsIgnoreCase("-inf"))
				return Integer.MIN_VALUE;
			else if (string.equalsIgnoreCase("inf"))
				return Integer.MAX_VALUE;

		}
		return 0;
	}

	/**
	 * attempt to parse an integer
	 *
	 * @param text the text
	 * @param base the base
	 * @return integer or 0
	 */
	public static int parseInt(String text, int base) {
		try {
			if (text != null) {
				var start = 0;
				while (start < text.length()) {
					var ch = text.charAt(start);
					if (Character.isLetterOrDigit(ch) || ch == '-')
						break;
					start++;
				}
				if (start < text.length()) {
					var finish = start + 1;
					while (finish < text.length() && isInteger(String.valueOf(text.charAt(finish)), base))
						finish++;
					if (start < finish)
						return Integer.parseInt(text.substring(start, finish), 16);
				}
			}
		} catch (Exception ex) {
			if (text.equalsIgnoreCase("-inf"))
				return Integer.MIN_VALUE;
			else if (text.equalsIgnoreCase("inf"))
				return Integer.MAX_VALUE;
		}
		return 0;
	}

	/**
	 * attempts to parse the string as a long, skipping leading chars and trailing characters, if necessary
	 *
	 * @return value or 0
	 */
	public static long parseLong(String string) {
		try {
			if (string != null) {
				var start = 0;
				while (start < string.length()) {
					var ch = string.charAt(start);
					if (Character.isDigit(ch) || ch == '+' || ch == '-')
						break;
					start++;
				}
				if (start < string.length()) {
					var finish = start + 1;
					while (finish < string.length() && Character.isDigit(string.charAt(finish)))
						finish++;
					if (start < finish)
						return Long.parseLong(string.substring(start, finish));
				}
			}
		} catch (Exception ex) {
			if (string.equalsIgnoreCase("-inf"))
				return Long.MIN_VALUE;
			else if (string.equalsIgnoreCase("inf"))
				return Long.MAX_VALUE;
		}
		return 0;
	}

	/**
	 * attempts to parse the string as an float, skipping leading chars and trailing characters, if necessary
	 *
	 * @return value or 0
	 */
	public static float parseFloat(String string) {
		try {
			if (string != null) {
				var start = 0;
				while (start < string.length()) {
					var ch = string.charAt(start);
					if (Character.isDigit(ch) || ch == '+' || ch == '-')
						break;
					start++;
				}
				if (start < string.length()) {
					var finish = start + 1;
					while (finish < string.length() && (Character.isDigit(string.charAt(finish)) || string.charAt(finish) == '.'
														|| string.charAt(finish) == 'E' || string.charAt(finish) == 'e' || string.charAt(finish) == '-'))
						finish++;
					if (start < finish)
						return Float.parseFloat(string.substring(start, finish));
				}
			}
		} catch (Exception ex) {
			if (string.equalsIgnoreCase("-inf"))
				return Float.NEGATIVE_INFINITY;
			else if (string.equalsIgnoreCase("inf"))
				return Float.POSITIVE_INFINITY;
		}
		return 0;
	}

	/**
	 * attempts to parse the string as a double, skipping leading chars and trailing characters, if necessary
	 *
	 * @return value or 0
	 */
	public static double parseDouble(String string) {
		try {
			if (string != null) {
				var start = 0;
				while (start < string.length()) {
					var ch = string.charAt(start);
					if (Character.isDigit(ch) || ch == '+' || ch == '-')
						break;
					start++;
				}
				if (start < string.length()) {
					var finish = start + 1;
					while (finish < string.length() && (Character.isDigit(string.charAt(finish)) || string.charAt(finish) == '.'
														|| string.charAt(finish) == 'E' || string.charAt(finish) == 'e' || string.charAt(finish) == '-' || string.charAt(finish) == '+'))
						finish++;
					if (start < finish)
						return Double.parseDouble(string.substring(start, finish));
				}
			}
		} catch (Exception ex) {
			if (string.equalsIgnoreCase("-inf"))
				return Double.NEGATIVE_INFINITY;
			else if (string.equalsIgnoreCase("inf"))
				return Double.POSITIVE_INFINITY;
		}
		return 0;
	}

	/**
	 * restrict a value to a given range
	 *
	 * @return value between min and max
	 */
	public static int restrictToRange(int min, int max, int value) {
		if (value < min)
			return min;
		return Math.min(value, max);
	}

	/**
	 * restrict a value to a given range
	 *
	 * @return value between min and max
	 */
	public static double restrictToRange(double min, double max, double value) {
		if (value < min)
			return min;
		return Math.min(value, max);
	}

	/**
	 * transposes a matrix
	 *
	 * @return transposed
	 */
	public static float[][] transposeMatrix(float[][] matrix) {
		final float[][] transposed = new float[matrix[0].length][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < transposed.length; j++) {
				transposed[j][i] = matrix[i][j];
			}
		}
		return transposed;
	}

	public static boolean equals(double a, double b, double threshold) {
		return Math.abs(a - b) <= threshold;
	}

	/**
	 * create a range of numbers
	 *
	 * @param low  lowest number (inclusive)
	 * @param high highest number (exclusive)
	 * @return all numbers [low,high)
	 */
	public static Collection<Integer> range(int low, int high) {
		var list = new ArrayList<Integer>();
		for (var i = low; i < high; i++)
			list.add(i);
		return list;
	}


	/**
	 * computes a set of numbers from a list of comma-separated values, and ranges a-b or a-b/step
	 *
	 * @param text
	 * @param allowZero
	 * @return
	 * @throws IOException
	 */
	public static Set<Integer> parsePositiveIntegers(String text, boolean allowZero) throws IOException {
		// var doubleRegEx = "(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)";
		var result = new TreeSet<Integer>();
		for (var part : StringUtils.split(text, ',')) {
			var matcher = Pattern.compile("^(\\d+)-(\\d+)/(\\d+)$").matcher(part);
			if (matcher.find()) {
				// Extract the first, second, and third numbers
				var a = Integer.parseInt(matcher.group(1));
				var b = Integer.parseInt(matcher.group(2));
				var step = Integer.parseInt(matcher.group(3));
				if ((a > 0 || allowZero && a == 0) && a <= b && step > 0) {
					for (var v = a; v <= b; v += step) {
						result.add(v);
					}
				} else
					throw new IOException("Failed to parse: " + part);
			} else {
				matcher = Pattern.compile("^(\\d+)-(\\d+)$").matcher(part);
				if (matcher.find()) {
					var a = Integer.parseInt(matcher.group(1));
					var b = Integer.parseInt(matcher.group(2));

					if ((a > 0 || a == 0 && allowZero) && a <= b) {
						if (a == 0 && b == 0)
							result.add(0);
						else {
							var step = 1;
							for (var v = a; v <= b; v += step) {
								result.add(v);
							}
						}
					} else
						throw new IOException("Failed to parse: " + part);
				} else {
					matcher = Pattern.compile("^(\\d+)$").matcher(part);
					if (matcher.find()) {
						var a = Integer.parseInt(matcher.group(1));
						if (a > 0 || a == 0 && allowZero)
							result.add(a);
						else
							throw new IOException("Failed to parse: " + part);
					} else
						throw new IOException("Failed to parse: " + part);
				}
			}
		}
		return result;
	}


	/**
	 * computes a set of numbers from a list of comma-separated values, and ranges a-b or a-b/step
	 *
	 * @param text
	 * @param allowZero
	 * @return
	 * @throws IOException
	 */
	public static Set<Double> parsePositiveDoubles(String text, boolean allowZero) throws IOException {
		// var doubleRegEx = "(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)";
		var result = new TreeSet<Double>();
		for (var part : StringUtils.split(text, ',')) {
			var matcher = Pattern.compile("^(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)-(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)/(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)$").matcher(part);
			if (matcher.find()) {
				// Extract the first, second, and third numbers
				var a = Double.parseDouble(matcher.group(1));
				var b = Double.parseDouble(matcher.group(2));
				var step = Double.parseDouble(matcher.group(3));
				if ((a > 0 || allowZero && a == 0) && a <= b && step > 0) {
					for (var v = a; v <= b; v += step) {
						result.add(v);
					}
				} else
					throw new IOException("Failed to parse: " + part);
			} else {
				matcher = Pattern.compile("^(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)-(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)$").matcher(part);
				if (matcher.find()) {
					var a = Double.parseDouble(matcher.group(1));
					var b = Double.parseDouble(matcher.group(2));

					if ((a > 0 || a == 0 && allowZero) && a <= b) {
						if (a == 0 && b == 0)
							result.add(0.0);
						else {
							var step = computeSmallestDigit(a > 0 ? a : b);
							for (var v = a; v <= b; v += step) {
								result.add(v);
							}
						}
					} else
						throw new IOException("Failed to parse: " + part);
				} else {
					matcher = Pattern.compile("^(\\d+\\.\\d+|\\d+\\.?|\\.\\d+)$").matcher(part);
					if (matcher.find()) {
						var a = Double.parseDouble(matcher.group(1));
						if (a > 0 || a == 0 && allowZero)
							result.add(a);
						else
							throw new IOException("Failed to parse: " + part);
					} else
						throw new IOException("Failed to parse: " + part);
				}
			}
		}
		return result;
	}

	/**
	 * gets the smallest digit in a number, e.g. 0.123 will result in 0.001
	 *
	 * @param value the input value
	 * @return the smallest digit
	 */
	public static double computeSmallestDigit(double value) {
		var df = new DecimalFormat("0.##########");
		var valueString = df.format(value);
		if (valueString.endsWith("."))
			return 1.0;
		else {
			var buf = new StringBuilder();
			for (var i = 0; i < valueString.length() - 1; i++) {
				if (valueString.charAt(i) == '.')
					buf.append(".");
				else buf.append("0");
			}
			buf.append(1);
			return Double.parseDouble(buf.toString());
		}
	}

	public static String toString(String format, double value) {
		return StringUtils.removeTrailingZerosAfterDot(String.format(format, value));
	}
}
