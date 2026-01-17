/*
 * PrintStreamForTextArea.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.message;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import jloda.util.Basic;
import jloda.util.StringUtils;

import java.io.PrintStream;

/**
 * print stream that sends text to a list view
 * Daniel Huson, 11.2022
 */
public class PrintStreamForListView extends PrintStream {
	public boolean echoToConsole = false;
	private final ListView<String> listView;
	private final int maxLines;

	public PrintStreamForListView(ListView<String> listView) {
		this(listView, Integer.MAX_VALUE);
	}

	public PrintStreamForListView(ListView<String> listView, int maxLines) {
		super(System.out);
		this.listView = listView;
		this.maxLines = maxLines;
	}

	public void println(String x) {
		add(x + "\n");
	}

	public void print(String x) {
		add(x);
	}

	public void println(Object x) {
		add(x + "\n");
	}

	public void print(Object x) {
		add(x == null ? null : x.toString());
	}

	public void println(boolean x) {
		add(x + "\n");
	}

	public void print(boolean x) {
		add("" + x);
	}

	public void println(int x) {
		add(x + "\n");
	}

	public void print(int x) {
		add("" + x);
	}

	public void println(float x) {
		add(x + "\n");
	}

	public void print(float x) {
		add("" + x);
	}

	public void println(char x) {
		add(x + "\n");
	}

	public void print(char x) {
		add("" + x);
	}

	public void println(double x) {
		add(x + "\n");
	}

	public void print(double x) {
		add("" + x);
	}

	public void println(long x) {
		add(x + "\n");
	}

	public void print(long x) {
		add("" + x);
	}


	public void println(char[] x) {
		add(StringUtils.toString(x) + "\n");
	}

	public void print(char[] x) {
		add(StringUtils.toString(x));
	}

	public void write(byte[] buf, int off, int len) {
		add(new String(buf, off, len));
	}

	public void setError() {
	}

	private void add(String message) {
		if (echoToConsole)
			Basic.getOrigErr().print(message);
		Platform.runLater(() -> {
			listView.getItems().add(message);
			if (listView.getItems().size() > maxLines) {
				listView.getItems().remove(0, listView.getItems().size() - maxLines);
			}
			listView.scrollTo(listView.getItems().size() - 1);
		});
	}

	public boolean checkError() {
		return false;
	}

	public void flush() {
	}

}
