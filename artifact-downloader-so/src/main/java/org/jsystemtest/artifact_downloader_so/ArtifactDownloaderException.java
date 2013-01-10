package org.jsystemtest.artifact_downloader_so;

public class ArtifactDownloaderException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ArtifactDownloaderException(String message) {
		super(message);
	}

	public ArtifactDownloaderException(String message, Exception e) {
		super(message, e);
	}

}
