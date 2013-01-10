package org.jsystemtest.artifact_downloader_so;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.w3c.dom.Document;

import jsystem.framework.system.SystemObjectImpl;
import junit.framework.Assert;

/**
 * System Object to download artifact from remote Maven repository.
 * 
 */
public class ArtifactDownloader extends SystemObjectImpl {

	/**
	 * If set to false. Will not init and download artifacts
	 */
	private boolean enabled = true;

	/**
	 * 
	 */
	private String artifactRepository = "http://80.74.108.9/content/repositories/snapshots";

	/**
	 * Artifact group id
	 */
	private String groupId = "org.jsystemtest";

	/**
	 * The actual artifact id
	 */
	private String artifactId = "jsystemCore";

	/**
	 * If the version ends with SNAPSHOT, the download will try to find the
	 * latest version
	 * 
	 */
	private String version = "6.0.00-SNAPSHOT";

	private String extension = "jar";

	/**
	 * if has value the artifact will be renamed to this name.
	 */
	private String renameFileTo;

	/**
	 * The location on which to put the downloadable artifact.
	 */
	private String destinationFolder = ".";

	private String classifier;

	public void init() throws Exception {
		super.init();
		if (enabled) {
			// Check parameters
			Assert.assertNotNull("Please specify artifactRepository", artifactRepository);
			if (!artifactRepository.startsWith("http://")) {
				artifactRepository = "http://" + artifactRepository;
			}
			Assert.assertNotNull("Please specify groupId", groupId);
			Assert.assertNotNull("Please specify artifactId", artifactId);
			Assert.assertNotNull("Please specify version", version);

		}

	}

	/**
	 * Download the file from the specified URL
	 * 
	 * @return The actual file downloaded.
	 */
	private File downloadFile(String url) throws ArtifactDownloaderException {
		report.report("About to download artifact from " + url);
		final HttpClient client = new HttpClient();
		final HttpMethod method = new GetMethod(url);
		File file = null;
		try {
			// Execute the method.
			int statusCode = client.executeMethod(method);

			if (statusCode != HttpStatus.SC_OK) {
				throw new ArtifactDownloaderException("Download failed  " + method.getStatusLine());
			}

			// Read the response body.
			byte[] responseBody = method.getResponseBody();

			file = new File(destinationFolder, url.replaceAll("http:.*/", ""));
			FileUtils.writeByteArrayToFile(file, responseBody);
		} catch (HttpException e) {
			throw new ArtifactDownloaderException("Fatal protocol violation:", e);
		} catch (IOException e) {
			throw new ArtifactDownloaderException("Failed writing file", e);
		} finally {
			// Release the connection.
			method.releaseConnection();
		}
		report.report("Download finished");
		return file;

	}

	public void download() {
		if (!enabled) {
			return;
		}
		try {
			String url = prepareUrl();
			File artifact = downloadFile(url);
			rename(artifact);

		} catch (ArtifactDownloaderException e) {
			report.report(e.getMessage(), e);
		} catch (IOException e) {
			report.report("Failed renaming artifcat ", e);
		}
	}

	private void rename(File artifact) throws IOException {
		if (renameFileTo == null || renameFileTo.isEmpty()) {
			return;
		}
		if (!artifact.exists()) {
			return;
		}

		File newArtifact = new File(destinationFolder, renameFileTo);
		report.report("Renaming artifact " + artifact.getCanonicalPath() + " to " + newArtifact.getCanonicalPath());
		if (newArtifact.exists()) {
			newArtifact.delete();
		}
		artifact.renameTo(newArtifact);

	}

	/**
	 * Prepare the URL according the the various artifacts parameters.Supports
	 * snapshot versions.
	 * 
	 * @return The URL for the artifact
	 * @throws ArtifactDownloaderException
	 */
	private String prepareUrl() throws ArtifactDownloaderException {
		StringBuilder url = new StringBuilder();
		url.append(artifactRepository);
		url.append("/");
		url.append(groupId.replace('.', '/'));
		url.append("/");
		url.append(artifactId);
		url.append("/");
		url.append(version);
		url.append("/");
		String actualVersion = null;
		if (version.contains("SNAPSHOT")) {
			// If the version is snapshot version we need to query the
			// repository and find the latest version.
			String metadata = getMetadata(url);
			actualVersion = getVersionFromMetadata(metadata);
		} else {
			actualVersion = version;
		}
		url.append(artifactId);
		url.append("-");
		url.append(actualVersion);
		if (classifier != null && !classifier.isEmpty()){
			url.append("-");
			url.append(classifier);
		}
		url.append(".");
		url.append(extension);
		

		return url.toString();
	}

	/**
	 * Gets the meta data that from the repository that we need for the snapshot
	 * file version.
	 * 
	 * @param url
	 * @return
	 * @throws ArtifactDownloaderException
	 */
	private String getMetadata(StringBuilder url) throws ArtifactDownloaderException {
		final String metadDataURL = url.toString() + "maven-metadata.xml";
		final HttpClient client = new HttpClient();
		final HttpMethod method = new GetMethod(metadDataURL);
		String metadata = null;
		try {
			// Execute the method.
			int statusCode = client.executeMethod(method);

			if (statusCode != HttpStatus.SC_OK) {
				throw new ArtifactDownloaderException("Download failed  " + method.getStatusLine());
			}

			// Read the response body.
			byte[] responseBody = method.getResponseBody();
			metadata = new String(responseBody);

		} catch (HttpException e) {
			throw new ArtifactDownloaderException("Fatal protocol violation:", e);
		} catch (IOException e) {
			throw new ArtifactDownloaderException("Failed writing file", e);
		} finally {
			// Release the connection.
			method.releaseConnection();
		}
		return metadata;
	}

	/**
	 * Parses the meta data for the latest snapshot version
	 * 
	 * @param metadata
	 * @return snapshot version
	 * @throws ArtifactDownloaderException
	 */
	private String getVersionFromMetadata(String metadata) throws ArtifactDownloaderException {
		try {

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(false);
			DocumentBuilder builder = docFactory.newDocumentBuilder();
			Document doc = builder.parse(new StringInputStream(metadata));
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			String xpathStr = null;
			if (classifier == null || classifier.isEmpty()) {
				xpathStr = "//snapshotVersion[contains(extension,'" + extension
						+ "') and not(contains(classifier,'sources'))]/value/text()";
			} else {
				xpathStr = "//snapshotVersion[contains(extension,'" + extension + "') and (contains(classifier,'"
						+ classifier + "'))]/value/text()";

			}
			XPathExpression expr = xpath.compile(xpathStr);
			return (String) expr.evaluate(doc, XPathConstants.STRING);
		} catch (Exception e) {
			throw new ArtifactDownloaderException("Failed to get version from metadata", e);
		}
	}

	public static void main(String[] args) throws ArtifactDownloaderException {
		ArtifactDownloader downloader = new ArtifactDownloader();
		downloader.download();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getArtifactRepository() {
		return artifactRepository;
	}

	public void setArtifactRepository(String artifactRepository) {
		this.artifactRepository = artifactRepository;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String type) {
		this.extension = type;
	}

	public String getDestinationFolder() {
		return destinationFolder;
	}

	public void setDestinationFolder(String destinationFolder) {
		this.destinationFolder = destinationFolder;
	}

	public String getRenameFileTo() {
		return renameFileTo;
	}

	public void setRenameFileTo(String renameFileTo) {
		this.renameFileTo = renameFileTo;
	}

	public String getClassifier() {
		return classifier;
	}

	public void setClassifier(String classifier) {
		this.classifier = classifier;
	}

}
