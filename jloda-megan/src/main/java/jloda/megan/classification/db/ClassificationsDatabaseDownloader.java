/*
 * ClassificationsDatabaseDownloader.java Copyright (C) 2026 Daniel H. Huson
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
package jloda.megan.classification.db;

import jloda.util.CanceledException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * downloads a classification database listed in a {@link ClassificationsManifest}: streams it to a temporary
 * file (reporting progress), verifies its sha256 checksum, then atomically moves it into place. Mirrors the
 * download-and-verify approach of MEGAN's installer updater.
 * <p>
 * Daniel Huson, 7.2026
 */
public class ClassificationsDatabaseDownloader {
	/**
	 * receives download progress; throw {@link CanceledException} to abort the download (e.g. on a Cancel button)
	 */
	@FunctionalInterface
	public interface ProgressHandler {
		void onProgress(long bytesRead, long totalBytes) throws CanceledException;
	}

	private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

	/**
	 * downloads the given database into the target directory as {@code <name>.db}, verifying its checksum
	 *
	 * @param entry           the manifest entry to download
	 * @param targetDirectory the directory to place the file in (created if necessary)
	 * @param progress        progress handler, or null
	 * @return the downloaded file
	 * @throws IOException       on a network, checksum or file error
	 * @throws CanceledException if the progress handler aborts the download
	 */
	public static File download(ClassificationsManifest.Entry entry, File targetDirectory, ProgressHandler progress) throws IOException, CanceledException {
		if (entry.getUrl() == null || entry.getUrl().isBlank())
			throw new IOException("Catalog entry has no download URL: " + entry.getName());
		if (!targetDirectory.exists() && !targetDirectory.mkdirs())
			throw new IOException("Could not create directory: " + targetDirectory);

		final var uri = URI.create(entry.getUrl());
		final var target = new File(targetDirectory, entry.getName() + ".db").toPath();
		final var tmp = target.resolveSibling(target.getFileName() + ".download");

		Files.deleteIfExists(tmp);

		final HttpResponse<InputStream> response;
		try {
			response = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Download interrupted", e);
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300)
			throw new IOException("Download failed: HTTP " + response.statusCode() + " from " + uri);

		final var totalBytes = response.headers().firstValueAsLong("content-length").orElse(entry.getSize() > 0 ? entry.getSize() : -1L);
		try {
			copyWithProgress(response.body(), tmp, totalBytes, progress);
			if (entry.getSha256() != null && !entry.getSha256().isBlank())
				verifyChecksum(tmp, entry.getSha256());
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) { // note: CanceledException is a subclass of IOException
			Files.deleteIfExists(tmp);
			throw ex;
		}
		return target.toFile();
	}

	private static void copyWithProgress(InputStream inputStream, Path target, long totalBytes, ProgressHandler progress) throws IOException, CanceledException {
		try (var in = inputStream; var out = Files.newOutputStream(target)) {
			final var buffer = new byte[1024 * 128];
			long totalRead = 0;
			int read;
			if (progress != null)
				progress.onProgress(0, totalBytes);
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
				totalRead += read;
				if (progress != null)
					progress.onProgress(totalRead, totalBytes);
			}
		}
	}

	private static void verifyChecksum(Path file, String expected) throws IOException {
		final var actual = sha256(file);
		if (!actual.equalsIgnoreCase(expected.trim()))
			throw new IOException("Checksum verification failed for " + file.getFileName() + "\nExpected: " + expected + "\nActual:   " + actual);
	}

	private static String sha256(Path file) throws IOException {
		try {
			final var digest = MessageDigest.getInstance("SHA-256");
			try (var in = Files.newInputStream(file)) {
				final var buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) > 0)
					digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
}
