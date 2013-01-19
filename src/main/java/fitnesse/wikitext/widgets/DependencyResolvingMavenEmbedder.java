package fitnesse.wikitext.widgets;

import hudson.maven.MavenEmbedderException;
import hudson.maven.MavenEmbedderUtils;
import hudson.maven.MavenRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.NotImplementedException;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.RepositorySystemSession;

/**
 * Copied from Hudson's Maven Embedded which hides most of this behind private methods, making it impossible to change
 * it's behavior. Only change is the addition of buildProject which builds the project using dependency resolution.
 */
public class DependencyResolvingMavenEmbedder {

	public static final String userHome = System.getProperty("user.home");

	private MavenXpp3Reader modelReader;
	private MavenXpp3Writer modelWriter;

	private final File mavenHome;
	private final PlexusContainer plexusContainer;
	private final MavenRequest mavenRequest;
	private MavenExecutionRequest mavenExecutionRequest;
	private final MavenSession mavenSession;

	public DependencyResolvingMavenEmbedder(final File mavenHome, final MavenRequest mavenRequest)
			throws MavenEmbedderException {
		this(mavenHome, mavenRequest, MavenEmbedderUtils.buildPlexusContainer(mavenHome, mavenRequest));
	}

	public DependencyResolvingMavenEmbedder(final ClassLoader mavenClassLoader, final ClassLoader parent,
			final MavenRequest mavenRequest) throws MavenEmbedderException {
		this(null, mavenRequest, MavenEmbedderUtils.buildPlexusContainer(mavenClassLoader, parent, mavenRequest));
	}

	private DependencyResolvingMavenEmbedder(final File mavenHome, final MavenRequest mavenRequest,
			final PlexusContainer plexusContainer) throws MavenEmbedderException {
		this.mavenHome = mavenHome;
		this.mavenRequest = mavenRequest;
		this.plexusContainer = plexusContainer;

		try {
			this.buildMavenExecutionRequest();

			final RepositorySystemSession rss = ((DefaultMaven) lookup(Maven.class))
					.newRepositorySession(mavenExecutionRequest);

			mavenSession = new MavenSession(plexusContainer, rss, mavenExecutionRequest, new DefaultMavenExecutionResult());

			lookup(LegacySupport.class).setSession(mavenSession);
		} catch (final MavenEmbedderException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		} catch (final ComponentLookupException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		}
	}

	public DependencyResolvingMavenEmbedder(final ClassLoader mavenClassLoader, final MavenRequest mavenRequest)
			throws MavenEmbedderException {
		this(mavenClassLoader, null, mavenRequest);
	}

	private void buildMavenExecutionRequest() throws MavenEmbedderException, ComponentLookupException {
		this.mavenExecutionRequest = new DefaultMavenExecutionRequest();

		if (this.mavenRequest.getGlobalSettingsFile() != null) {
			this.mavenExecutionRequest.setGlobalSettingsFile(this.mavenExecutionRequest.getGlobalSettingsFile());
		}

		if (this.mavenExecutionRequest.getUserSettingsFile() != null) {
			this.mavenExecutionRequest.setUserSettingsFile(new File(mavenRequest.getUserSettingsFile()));
		}

		try {
			lookup(MavenExecutionRequestPopulator.class).populateFromSettings(this.mavenExecutionRequest, getSettings());

			lookup(MavenExecutionRequestPopulator.class).populateDefaults(mavenExecutionRequest);
		} catch (final MavenExecutionRequestPopulationException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		}

		final ArtifactRepository localRepository = getLocalRepository();
		this.mavenExecutionRequest.setLocalRepository(localRepository);
		this.mavenExecutionRequest.setLocalRepositoryPath(localRepository.getBasedir());
		this.mavenExecutionRequest.setOffline(this.mavenExecutionRequest.isOffline());

		this.mavenExecutionRequest.setUpdateSnapshots(this.mavenRequest.isUpdateSnapshots());

		// TODO check null and create a console one ?
		this.mavenExecutionRequest.setTransferListener(this.mavenRequest.getTransferListener());

		//		this.mavenExecutionRequest.setCacheNotFound(this.mavenRequest.isCacheNotFound());
		this.mavenExecutionRequest.setCacheNotFound(false);
		this.mavenExecutionRequest.setCacheTransferError(false);

		this.mavenExecutionRequest.setUserProperties(this.mavenRequest.getUserProperties());
		this.mavenExecutionRequest.getSystemProperties().putAll(System.getProperties());
		if (this.mavenRequest.getSystemProperties() != null) {
			this.mavenExecutionRequest.getSystemProperties().putAll(this.mavenRequest.getSystemProperties());
		}
		this.mavenExecutionRequest.getSystemProperties().putAll(getEnvVars());

		if (this.mavenHome != null) {
			this.mavenExecutionRequest.getSystemProperties().put("maven.home", this.mavenHome.getAbsolutePath());
		}

		if (this.mavenRequest.getProfiles() != null && !this.mavenRequest.getProfiles().isEmpty()) {
			for (final String id : this.mavenRequest.getProfiles()) {
				final Profile p = new Profile();
				p.setId(id);
				p.setSource("cli");
				this.mavenExecutionRequest.addProfile(p);
				this.mavenExecutionRequest.addActiveProfile(id);
			}
		}

		this.mavenExecutionRequest.setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_WARN);

		lookup(Logger.class).setThreshold(Logger.LEVEL_WARN);

		this.mavenExecutionRequest.setExecutionListener(this.mavenRequest.getExecutionListener())
				.setInteractiveMode(this.mavenRequest.isInteractive())
				.setGlobalChecksumPolicy(this.mavenRequest.getGlobalChecksumPolicy()).setGoals(this.mavenRequest.getGoals());

		if (this.mavenRequest.getPom() != null) {
			this.mavenExecutionRequest.setPom(new File(this.mavenRequest.getPom()));
		}

		if (this.mavenRequest.getWorkspaceReader() != null) {
			this.mavenExecutionRequest.setWorkspaceReader(this.mavenRequest.getWorkspaceReader());
		}
	}

	private Properties getEnvVars() {
		final Properties envVars = new Properties();
		final boolean caseSensitive = !Os.isFamily(Os.FAMILY_WINDOWS);
		for (final Map.Entry<String, String> entry : System.getenv().entrySet()) {
			final String key = "env." + (caseSensitive ? entry.getKey() : entry.getKey().toUpperCase(Locale.ENGLISH));
			envVars.setProperty(key, entry.getValue());
		}
		return envVars;
	}

	public Settings getSettings() throws MavenEmbedderException, ComponentLookupException {

		final SettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
		if (this.mavenRequest.getGlobalSettingsFile() != null) {
			settingsBuildingRequest.setGlobalSettingsFile(new File(this.mavenRequest.getGlobalSettingsFile()));
		} else {
			settingsBuildingRequest.setGlobalSettingsFile(MavenCli.DEFAULT_GLOBAL_SETTINGS_FILE);
		}
		if (this.mavenRequest.getUserSettingsFile() != null) {
			settingsBuildingRequest.setUserSettingsFile(new File(this.mavenRequest.getUserSettingsFile()));
		} else {
			settingsBuildingRequest.setUserSettingsFile(MavenCli.DEFAULT_USER_SETTINGS_FILE);
		}

		settingsBuildingRequest.setUserProperties(this.mavenRequest.getUserProperties());
		settingsBuildingRequest.getSystemProperties().putAll(System.getProperties());
		settingsBuildingRequest.getSystemProperties().putAll(this.mavenRequest.getSystemProperties());
		settingsBuildingRequest.getSystemProperties().putAll(getEnvVars());

		try {
			return lookup(SettingsBuilder.class).build(settingsBuildingRequest).getEffectiveSettings();
		} catch (final SettingsBuildingException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		}
	}

	public ArtifactRepository getLocalRepository() throws ComponentLookupException {
		try {
			final String localRepositoryPath = getLocalRepositoryPath();
			if (localRepositoryPath != null) {
				return lookup(RepositorySystem.class).createLocalRepository(new File(localRepositoryPath));
			}
			return lookup(RepositorySystem.class).createLocalRepository(RepositorySystem.defaultUserLocalRepository);
		} catch (final InvalidRepositoryException e) {
			// never happened
			throw new IllegalStateException(e);
		}
	}

	public String getLocalRepositoryPath() {
		String path = null;

		try {
			final Settings settings = getSettings();
			path = settings.getLocalRepository();
		} catch (final MavenEmbedderException e) {
			// ignore
		} catch (final ComponentLookupException e) {
			// ignore
		}

		if (this.mavenRequest.getLocalRepositoryPath() != null) {
			path = this.mavenRequest.getLocalRepositoryPath();
		}

		if (path == null) {
			path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
		}
		return path;
	}

	// ----------------------------------------------------------------------
	// Model
	// ----------------------------------------------------------------------

	public Model readModel(final File model) throws XmlPullParserException, FileNotFoundException, IOException {
		return modelReader.read(new FileReader(model));
	}

	public void writeModel(final Writer writer, final Model model) throws IOException {
		modelWriter.write(writer, model);
	}

	// ----------------------------------------------------------------------
	// Project
	// ----------------------------------------------------------------------

	public MavenProject readProject(final File mavenProject) throws ProjectBuildingException, MavenEmbedderException {

		final List<MavenProject> projects = readProjects(mavenProject, false);
		return projects == null || projects.isEmpty() ? null : projects.get(0);

	}

	public List<MavenProject> readProjects(final File mavenProject, final boolean recursive)
			throws ProjectBuildingException, MavenEmbedderException {
		final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
		try {
			final List<ProjectBuildingResult> results = buildProjects(mavenProject, recursive);
			final List<MavenProject> projects = new ArrayList<MavenProject>(results.size());
			for (final ProjectBuildingResult result : results) {
				projects.add(result.getProject());
			}
			return projects;
		} finally {
			Thread.currentThread().setContextClassLoader(originalCl);
		}

	}

	public ProjectBuildingResult buildProject(final File mavenProject) throws ProjectBuildingException,
			MavenEmbedderException {
		final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.plexusContainer.getContainerRealm());
			final ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
			final ProjectBuildingRequest projectBuildingRequest = this.mavenExecutionRequest.getProjectBuildingRequest();

			projectBuildingRequest.setValidationLevel(this.mavenRequest.getValidationLevel());

			final RepositorySystemSession repositorySystemSession = buildRepositorySystemSession();

			projectBuildingRequest.setRepositorySession(repositorySystemSession);

			projectBuildingRequest.setProcessPlugins(this.mavenRequest.isProcessPlugins());

			projectBuildingRequest.setResolveDependencies(this.mavenRequest.isResolveDependencies());

			return projectBuilder.build(mavenProject, projectBuildingRequest);
		} catch (final ComponentLookupException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		} finally {
			Thread.currentThread().setContextClassLoader(originalCl);
		}

	}

	public List<ProjectBuildingResult> buildProjects(final File mavenProject, final boolean recursive)
			throws ProjectBuildingException, MavenEmbedderException {
		final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.plexusContainer.getContainerRealm());
			final ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
			final ProjectBuildingRequest projectBuildingRequest = this.mavenExecutionRequest.getProjectBuildingRequest();

			projectBuildingRequest.setValidationLevel(this.mavenRequest.getValidationLevel());

			final RepositorySystemSession repositorySystemSession = buildRepositorySystemSession();

			projectBuildingRequest.setRepositorySession(repositorySystemSession);

			projectBuildingRequest.setProcessPlugins(this.mavenRequest.isProcessPlugins());

			projectBuildingRequest.setResolveDependencies(this.mavenRequest.isResolveDependencies());

			final List<ProjectBuildingResult> results = projectBuilder.build(Arrays.asList(mavenProject), recursive,
					projectBuildingRequest);

			return results;
		} catch (final ComponentLookupException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		} finally {
			Thread.currentThread().setContextClassLoader(originalCl);
		}

	}

	private RepositorySystemSession buildRepositorySystemSession() throws ComponentLookupException {
		final DefaultMaven defaultMaven = (DefaultMaven) plexusContainer.lookup(Maven.class);
		return defaultMaven.newRepositorySession(mavenExecutionRequest);
	}

	public List<MavenProject> collectProjects(final File basedir, final String[] includes, final String[] excludes)
			throws MojoExecutionException, MavenEmbedderException {
		final List<MavenProject> projects = new ArrayList<MavenProject>();

		final List<File> poms = getPomFiles(basedir, includes, excludes);

		for (final File pom : poms) {
			try {
				final MavenProject p = readProject(pom);

				projects.add(p);

			} catch (final ProjectBuildingException e) {
				throw new MojoExecutionException("Error loading " + pom, e);
			}
		}

		return projects;
	}

	// ----------------------------------------------------------------------
	// Artifacts
	// ----------------------------------------------------------------------

	public Artifact createArtifact(final String groupId, final String artifactId, final String version,
			final String scope, final String type) throws MavenEmbedderException {
		try {
			final RepositorySystem repositorySystem = lookup(RepositorySystem.class);
			return repositorySystem.createArtifact(groupId, artifactId, version, scope, type);
		} catch (final ComponentLookupException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		}

	}

	public Artifact createArtifactWithClassifier(final String groupId, final String artifactId, final String version,
			final String type, final String classifier) throws MavenEmbedderException {
		try {
			final RepositorySystem repositorySystem = lookup(RepositorySystem.class);
			return repositorySystem.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
		} catch (final ComponentLookupException e) {
			throw new MavenEmbedderException(e.getMessage(), e);
		}
	}

	public void resolve(final Artifact artifact, final List<?> remoteRepositories,
			final ArtifactRepository localRepository) throws ArtifactResolutionException, ArtifactNotFoundException {

		// TODO to de implemented
		throw new NotImplementedException("resolve to be implemented");
	}

	// ----------------------------------------------------------------------
	// Execution of phases/goals
	// ----------------------------------------------------------------------

	public MavenExecutionResult execute(final MavenRequest mavenRequest) throws ComponentLookupException {
		final Maven maven = lookup(Maven.class);
		final ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.plexusContainer.getContainerRealm());
			return maven.execute(mavenExecutionRequest);
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	// ----------------------------------------------------------------------
	// Local Repository
	// ----------------------------------------------------------------------

	public static final String DEFAULT_LOCAL_REPO_ID = "local";

	public static final String DEFAULT_LAYOUT_ID = "default";

	public ArtifactRepository createLocalRepository(final File localRepository) throws ComponentLookupException {
		return createLocalRepository(localRepository.getAbsolutePath(), DEFAULT_LOCAL_REPO_ID);
	}

	public ArtifactRepository createLocalRepository(final Settings settings) throws ComponentLookupException {
		return createLocalRepository(settings.getLocalRepository(), DEFAULT_LOCAL_REPO_ID);
	}

	public ArtifactRepository createLocalRepository(String url, final String repositoryId)
			throws ComponentLookupException {
		if (!url.startsWith("file:")) {
			url = "file://" + url;
		}

		return createRepository(url, repositoryId);
	}

	public ArtifactRepository createRepository(final String url, final String repositoryId)
			throws ComponentLookupException {
		// snapshots vs releases
		// offline = to turning the update policy off

		//TODO: we'll need to allow finer grained creation of repositories but this will do for now

		final String updatePolicyFlag = ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS;

		final String checksumPolicyFlag = ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN;

		final ArtifactRepositoryPolicy snapshotsPolicy = new ArtifactRepositoryPolicy(true, updatePolicyFlag,
				checksumPolicyFlag);

		final ArtifactRepositoryPolicy releasesPolicy = new ArtifactRepositoryPolicy(true, updatePolicyFlag,
				checksumPolicyFlag);

		final RepositorySystem repositorySystem = lookup(RepositorySystem.class);

		final ArtifactRepositoryLayout repositoryLayout = lookup(ArtifactRepositoryLayout.class, "default");

		return repositorySystem.createArtifactRepository(repositoryId, url, repositoryLayout, snapshotsPolicy,
				releasesPolicy);

	}

	// ----------------------------------------------------------------------
	// Internal utility code
	// ----------------------------------------------------------------------

	private List<File> getPomFiles(final File basedir, final String[] includes, final String[] excludes) {
		final DirectoryScanner scanner = new DirectoryScanner();

		scanner.setBasedir(basedir);

		scanner.setIncludes(includes);

		scanner.setExcludes(excludes);

		scanner.scan();

		final List<File> poms = new ArrayList<File>();

		for (int i = 0; i < scanner.getIncludedFiles().length; i++) {
			poms.add(new File(basedir, scanner.getIncludedFiles()[i]));
		}

		return poms;
	}

	public <T> T lookup(final Class<T> clazz) throws ComponentLookupException {
		return plexusContainer.lookup(clazz);
	}

	public <T> T lookup(final Class<T> clazz, final String hint) throws ComponentLookupException {
		return plexusContainer.lookup(clazz, hint);
	}

	public Object lookup(final String role, final String hint) throws ComponentLookupException {
		return plexusContainer.lookup(role, hint);
	}

	public Object lookup(final String role) throws ComponentLookupException {
		return plexusContainer.lookup(role);
	}

	public MavenRequest getMavenRequest() {
		return mavenRequest;
	}
}
