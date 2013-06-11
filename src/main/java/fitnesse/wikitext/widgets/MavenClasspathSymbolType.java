package fitnesse.wikitext.widgets;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import util.Maybe;
import fitnesse.html.HtmlUtil;
import fitnesse.wikitext.parser.Matcher;
import fitnesse.wikitext.parser.Parser;
import fitnesse.wikitext.parser.Path;
import fitnesse.wikitext.parser.PathsProvider;
import fitnesse.wikitext.parser.Rule;
import fitnesse.wikitext.parser.Symbol;
import fitnesse.wikitext.parser.SymbolType;
import fitnesse.wikitext.parser.Translation;
import fitnesse.wikitext.parser.Translator;

/**
 * FitNesse SymbolType implementation which enables Maven classpath integration for FitNesse.
 */
public class MavenClasspathSymbolType extends SymbolType implements Rule, Translation, PathsProvider {

	private MavenClasspathExtractor mavenClasspathExtractor;

	public MavenClasspathSymbolType() {
		super("MavenClasspathSymbolType");
		this.mavenClasspathExtractor = new MavenClasspathExtractor();

		wikiMatcher(new Matcher().startLineOrCell().string("!pomFile"));

		wikiRule(this);
		htmlTranslation(this);
	}

	@Override
	public String toTarget(final Translator translator, final Symbol symbol) {

		final ExtractClasspathEntriesParameter parameter = parseParameters(translator, symbol);
		final List<String> classpathElements = mavenClasspathExtractor.extractClasspathEntries(parameter);

		final StringBuilder classpathForRender = new StringBuilder();
		classpathForRender.append("scope: ").append(parameter.scope).append(HtmlUtil.BRtag);
		if (parameter.profiles != null) {
			for (final String profile : parameter.profiles) {
				classpathForRender.append("profile: ").append(profile).append(HtmlUtil.BRtag);
			}
		}
		for (final String element : classpathElements) {
			classpathForRender.append(HtmlUtil.metaText("classpath: " + element)).append(HtmlUtil.BRtag);

		}
		return classpathForRender.toString();

	}

	private ExtractClasspathEntriesParameter parseParameters(final Translator translator, final Symbol symbol) {
		String pomFile = translator.translateTree(symbol);
		if (pomFile.endsWith("<br/>")) {
			pomFile = pomFile.substring(0, pomFile.length() - 5);
		}
		String scope = MavenClasspathExtractor.DEFAULT_SCOPE;
		List<String> profiles = null;

		if (pomFile.contains("@")) {
			final String[] s = pomFile.split("@");
			pomFile = s[0];
			scope = s[1];
			if (s.length > 2) {
				profiles = extractProfiles(s[2]);
			}
		}

		final ExtractClasspathEntriesParameter parameter = new ExtractClasspathEntriesParameter(new File(pomFile), scope,
				profiles);
		return parameter;
	}

	private List<String> extractProfiles(final String profiles) {
		if (profiles.contains("#")) {
			final String[] profilesArray = profiles.split("#");
			return Arrays.asList(profilesArray);
		}
		return Arrays.asList(profiles);
	}

	@Override
	public Maybe<Symbol> parse(final Symbol symbol, final Parser parser) {
		final Symbol next = parser.moveNext(1);

		if (!next.isType(SymbolType.Whitespace)) {
			return Symbol.nothing;
		}

		final Symbol symbol1 = parser.parseTo(SymbolType.Newline);
		symbol.add(symbol1);

		return new Maybe<Symbol>(symbol);
	}

	@Override
	public boolean matchesFor(final SymbolType symbolType) {
		return symbolType instanceof Path || super.matchesFor(symbolType);
	}

	/**
	 * Exposed for testing
	 */
	protected void setMavenClasspathExtractor(final MavenClasspathExtractor mavenClasspathExtractor) {
		this.mavenClasspathExtractor = mavenClasspathExtractor;
	}

	@Override
	public Collection<String> providePaths(final Translator translator, final Symbol symbol) {
		final ExtractClasspathEntriesParameter parameter = parseParameters(translator, symbol);
		return mavenClasspathExtractor.extractClasspathEntries(parameter);
	}
}
