/*
 * GraphLayouts.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.graph.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Entry point for obtaining a {@link GraphLayoutService}. Programs call {@link #getService()} to get
 * the preferred layout: a {@link ServiceLoader}-discovered provider (e.g. a native OGDF FM3 layout)
 * if one is on the module/class path, otherwise jloda's built-in Java implementation
 * ({@link JlodaFmmLayoutService}). Where a program wants to let the user choose, {@link #getServices()}
 * lists all available layouts (the built-in one plus any providers) and {@link #getService(String)}
 * looks one up by name.
 * <p>
 * The built-in Java layout is <em>not</em> registered as a service provider; it is added here as the
 * explicit fallback, so a discovered provider always takes precedence in {@link #getService()}.
 * Daniel Huson, 8.2026
 */
public class GraphLayouts {
	private static GraphLayoutService preferred;

	private GraphLayouts() {
	}

	/**
	 * the preferred layout service: the first discovered provider if any is present, otherwise the
	 * built-in jloda Java implementation. The result is cached.
	 */
	public static synchronized GraphLayoutService getService() {
		if (preferred == null) {
			preferred = ServiceLoader.load(GraphLayoutService.class).stream()
					.map(ServiceLoader.Provider::get)
					.filter(GraphLayoutService::isAvailable)
					.findFirst()
					.orElseGet(JlodaFmmLayoutService::new);
		}
		return preferred;
	}

	/**
	 * all usable layout services: the built-in jloda implementation plus any discovered providers that
	 * report themselves {@link GraphLayoutService#isAvailable() available}.
	 */
	public static List<GraphLayoutService> getServices() {
		var list = new ArrayList<GraphLayoutService>();
		list.add(new JlodaFmmLayoutService());
		for (var service : ServiceLoader.load(GraphLayoutService.class)) {
			if (service.isAvailable())
				list.add(service);
		}
		return list;
	}

	/**
	 * the available service with the given {@link GraphLayoutService#getName() name}, if any.
	 */
	public static Optional<GraphLayoutService> getService(String name) {
		return getServices().stream().filter(s -> s.getName().equals(name)).findFirst();
	}
}
