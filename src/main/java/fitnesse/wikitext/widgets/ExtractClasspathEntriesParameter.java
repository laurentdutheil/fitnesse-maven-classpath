package fitnesse.wikitext.widgets;

import java.io.File;
import java.util.List;

public class ExtractClasspathEntriesParameter {
	public final File pomFile;
	public final String scope;
	public final List<String> profiles;

	public ExtractClasspathEntriesParameter(File pomFile, String scope, List<String> profiles) {
		this.pomFile = pomFile;
		this.scope = scope;
		this.profiles = profiles;
	}
}