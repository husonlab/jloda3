/*
 * Notifications.java Copyright (C) 2026 Daniel H. Huson
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
package jloda.megan.util;

import java.util.function.BiConsumer;

/**
 * how this module tells a user something they need to see.
 * <p>
 * By default a message goes to standard error, which is what a command-line tool wants and what a headless
 * run gets. A program with a user interface installs its own handler at startup — MEGAN's delegates to
 * {@code jloda.swing.window.NotificationsInSwing}, so its users keep seeing the same popups they always did.
 * <p>
 * The point is that this module must not depend on Swing: it is shared with programs that have no Swing at
 * all, and every algorithm here has to work headless. A direct call to {@code NotificationsInSwing} would
 * drag the whole toolkit in, and replacing such calls with a bare {@code System.err.println} would quietly
 * downgrade the GUI. This keeps both.
 * <p>
 * Daniel Huson, 8.2026
 */
public class Notifications {
	/**
	 * how serious a message is. The handler decides what that looks like.
	 */
	public enum Kind {information, warning, error}

	private static BiConsumer<Kind, String> handler = Notifications::toStandardError;

	/**
	 * installs the handler that receives every message. Pass null to go back to standard error.
	 * <p>
	 * Called once at startup by a program with a user interface. Never call this from library code — the
	 * default is deliberately the headless one.
	 */
	public static void setHandler(BiConsumer<Kind, String> handler) {
		Notifications.handler = (handler != null ? handler : Notifications::toStandardError);
	}

	public static void showInformation(String message) {
		handler.accept(Kind.information, message);
	}

	public static void showWarning(String message) {
		handler.accept(Kind.warning, message);
	}

	public static void showError(String message) {
		handler.accept(Kind.error, message);
	}

	public static void showInternalError(String message) {
		handler.accept(Kind.error, "Internal error: " + message);
	}

	/**
	 * the default handler. The prefixes match what NotificationsInSwing writes when it finds itself headless,
	 * so a tool's output does not change depending on which of the two is in place.
	 */
	private static void toStandardError(Kind kind, String message) {
		System.err.println(switch (kind) {
			case information -> "Info: ";
			case warning -> "Warning: ";
			case error -> "Error: ";
		} + message);
	}
}
