package fitnesse.wikitext.widgets;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class MavenClasspathExtractorTest {

	private MavenClasspathExtractor mavenClasspathExtractor;
	private File pomFile;

	@Before
	public void setUp() {
		pomFile = new File(MavenClasspathExtractor.class.getClassLoader().getResource("MavenClasspathWidget/pom.xml")
				.getFile());

		mavenClasspathExtractor = new MavenClasspathExtractor();
	}

	@Test
	public void extractedClasspathIncludesTestScopeDependencies() {
		final List<String> classpathEntries = mavenClasspathExtractor.extractClasspathEntries(pomFile);
		final StringBuffer sb = new StringBuffer();
		for (final String cpEntry : classpathEntries) {
			sb.append(cpEntry);
		}

		final String path = sb.toString();

		assertEquals(3, classpathEntries.size());
		assertTrue(path.contains("commons-lang"));
	}

	@Test
	public void extractedClasspathIncludesTestScopeDependenciesCompile() {
		final List<String> classpathEntries = mavenClasspathExtractor
				.extractClasspathEntries(new ExtractClasspathEntriesParameter(pomFile, "compile", null));
		final StringBuffer sb = new StringBuffer();
		for (final String cpEntry : classpathEntries) {
			sb.append(cpEntry);
		}

		final String path = sb.toString();

		assertEquals(1, classpathEntries.size());
		assertFalse(path.contains("commons-lang"));
	}

	@Test
	public void extractedClasspathIncludesTestScopeDependenciesCompileProfile() {
		final List<String> profiles = Arrays.asList("subDep");
		final List<String> classpathEntries = mavenClasspathExtractor
				.extractClasspathEntries(new ExtractClasspathEntriesParameter(pomFile, "compile", profiles));
		final StringBuffer sb = new StringBuffer();
		for (final String cpEntry : classpathEntries) {
			sb.append(cpEntry);
		}

		final String path = sb.toString();

		assertEquals(1, classpathEntries.size());
		assertFalse(path.contains("commons-lang"));
		//		assertTrue(path.contains("fitnesse-subdep"));
	}

	@Test(expected = MavenClasspathExtractionException.class)
	public void failsOnNonExistingPom() {
		mavenClasspathExtractor.extractClasspathEntries(new File("test-pom.xml"));
	}

}
