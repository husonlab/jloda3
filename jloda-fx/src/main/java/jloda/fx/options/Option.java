/*
 *  Option.java Copyright (C) 2026 Daniel H. Huson
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
 */

package jloda.fx.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * marks a JavaFX property field as a user option that is to be saved and restored
 * <p>
 * Usage:
 * <pre>
 * &#64;Option(description = "Minimum confidence required for an edge", min = 0)
 * private final DoubleProperty confidenceThreshold = new SimpleDoubleProperty(this, "confidenceThreshold", 0.0);
 * </pre>
 * The option name defaults to the field name, with any leading "option" stripped and the
 * remainder converted to snake case, e.g. optionOutlineWidth -&gt; outline_width.
 * <p>
 * Daniel Huson, 7.2026
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
	/**
	 * the name under which the option is stored, defaults to the snake-case field name
	 */
	String name() default "";

	/**
	 * human-readable description, used in documentation and as a tool tip
	 */
	String description() default "";

	/**
	 * former names of this option, accepted when reading older files
	 */
	String[] aliases() default {};

	/**
	 * smallest acceptable value, for numerical options, NaN means unbounded
	 */
	double min() default Double.NaN;

	/**
	 * largest acceptable value, for numerical options, NaN means unbounded
	 */
	double max() default Double.NaN;

	/**
	 * acceptable values, for string options. For enum options these are determined automatically
	 */
	String[] legalValues() default {};

	/**
	 * is this option to be written to and read from files?
	 */
	boolean persist() default true;
}
