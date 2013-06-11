package fitnesse.wikitext.widgets;

import hudson.maven.MavenEmbedderException;
import hudson.maven.MavenRequest;

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * Utiity class to extract classpath elements from Maven projects. Heavily based on code copied from Jenkin's Maven
 * support.
 */
public class MavenClasspathExtractor {

	public final static String DEFAULT_SCOPE = "test";
	public static final String FILE_SEPARATOR = System.getProperty("file.separator");

	private File userSettingsFile;
	private File globalSettingsFile;

	public List<String> extractClasspathEntries(final File pomFile) {
		return extractClasspathEntries(new ExtractClasspathEntriesParameter(pomFile, DEFAULT_SCOPE, null));
	}

	public List<String> extractClasspathEntries(final ExtractClasspathEntriesParameter parameter)
			throws MavenClasspathExtractionException {

		try {
			MavenRequest mavenRequest = mavenConfiguration();
			mavenRequest.setResolveDependencies(true);
			mavenRequest.setBaseDirectory(parameter.pomFile.getParent());
			mavenRequest.setShowErrors(true);
			mavenRequest.setPom(parameter.pomFile.getAbsolutePath());
			if (parameter.profiles != null && !parameter.profiles.isEmpty()) {
				mavenRequest.setProfiles(parameter.profiles);
			}

			DependencyResolvingMavenEmbedder dependencyResolvingMavenEmbedder = new DependencyResolvingMavenEmbedder(
					getClass().getClassLoader(), mavenRequest);

			ProjectBuildingResult projectBuildingResult = dependencyResolvingMavenEmbedder.buildProject(parameter.pomFile);
			return getClasspathForScope(projectBuildingResult, parameter.scope);

		} catch (MavenEmbedderException mee) {
			throw new MavenClasspathExtractionException(mee);
		} catch (ComponentLookupException cle) {
			throw new MavenClasspathExtractionException(cle);
		} catch (DependencyResolutionRequiredException e) {
			throw new MavenClasspathExtractionException(e);
		} catch (ProjectBuildingException e) {
			throw new MavenClasspathExtractionException(e);
		}
	}

	private List<String> getClasspathForScope(final ProjectBuildingResult projectBuildingResult, final String scope)
			throws DependencyResolutionRequiredException {
		MavenProject project = projectBuildingResult.getProject();

		if ("compile".equalsIgnoreCase(scope)) {
			return project.getCompileClasspathElements();
		} else if ("runtime".equalsIgnoreCase(scope)) {
			return project.getRuntimeClasspathElements();
		}
		return project.getTestClasspathElements();

	}

	// protected for test purposes
	protected MavenRequest mavenConfiguration() throws MavenEmbedderException, ComponentLookupException {
		MavenRequest mavenRequest = new MavenRequest();
		globalSettingsFile = new File(System.getenv("MAVEN_HOME") + FILE_SEPARATOR + "conf" + FILE_SEPARATOR
				+ "settings.xml");
		if (!globalSettingsFile.exists())
			globalSettingsFile = new File(System.getenv("M2_HOME") + FILE_SEPARATOR + "conf" + FILE_SEPARATOR
					+ "settings.xml");
		if (!globalSettingsFile.exists())
			globalSettingsFile = new File(System.getenv("M3_HOME") + FILE_SEPARATOR + "conf" + FILE_SEPARATOR
					+ "settings.xml");

		userSettingsFile = new File(System.getenv("user.home") + FILE_SEPARATOR + ".m2" + FILE_SEPARATOR + "settings.xml");

		if (globalSettingsFile != null && globalSettingsFile.exists()) {
			mavenRequest.setGlobalSettingsFile(globalSettingsFile.getAbsolutePath());
		}
		if (userSettingsFile != null && userSettingsFile.exists()) {
			mavenRequest.setUserSettingsFile(userSettingsFile.getAbsolutePath());
		}

		DependencyResolvingMavenEmbedder mavenEmbedder = new DependencyResolvingMavenEmbedder(
				MavenClasspathExtractor.class.getClassLoader(), mavenRequest);
		mavenEmbedder.getMavenRequest().setLocalRepositoryPath(
				getLocalRepository(mavenEmbedder.getSettings().getLocalRepository()));

		//TODO Add base directory
		return mavenEmbedder.getMavenRequest();
	}

	/*
	 * can be overridden for test purposes.
	 */
	protected String getLocalRepository(final String localRepository) {
		return localRepository;
	}

	// protected for test purposes
	protected void setMavenUserSettingsFile(final File userSettingsFile) {
		this.userSettingsFile = userSettingsFile;
	}

	// protected for test purposes
	protected void setMavenGlobalSettingsFile(final File globalSettingsFile) {
		this.globalSettingsFile = globalSettingsFile;
	}

}
