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
	public String toTarget(Translator translator, Symbol symbol) {

		List<String> classpathElements = getClasspathElements(translator, symbol);

		String classpathForRender = "";
		for (String element : classpathElements) {
			classpathForRender += HtmlUtil.metaText("classpath: " + element) + HtmlUtil.BRtag;

		}
		return classpathForRender;

	}

	private List<String> getClasspathElements(Translator translator, Symbol symbol) {
		String pomFile = translator.translateTree(symbol);
		String scope = MavenClasspathExtractor.DEFAULT_SCOPE;
		List<String> profiles = null;

		if (pomFile.contains("@")) {
			String[] s = pomFile.split("@");
			pomFile = s[0];
			scope = s[1];
			if (s.length > 2) {
				profiles = extractProfiles(s[2]);
			}
		}

		return mavenClasspathExtractor.extractClasspathEntries(new File(pomFile), scope, profiles);
	}

	private List<String> extractProfiles(String profiles) {
		if (profiles.contains("#")) {
			String[] profilesArray = profiles.split("#");
			return Arrays.asList(profilesArray);
		}
		return Arrays.asList(profiles);
	}

	@Override
	public Maybe<Symbol> parse(Symbol symbol, Parser parser) {
		Symbol next = parser.moveNext(1);

		if (!next.isType(SymbolType.Whitespace)) {
			return Symbol.nothing;
		}

		Symbol symbol1 = parser.parseTo(SymbolType.EndCell);
		symbol.add(symbol1);

		return new Maybe<Symbol>(symbol);
	}

	@Override
	public boolean matchesFor(SymbolType symbolType) {
		return symbolType instanceof Path || super.matchesFor(symbolType);
	}

	/**
	 * Exposed for testing
	 */
	protected void setMavenClasspathExtractor(MavenClasspathExtractor mavenClasspathExtractor) {
		this.mavenClasspathExtractor = mavenClasspathExtractor;
	}

	@Override
	public Collection<String> providePaths(Translator translator, Symbol symbol) {
		return getClasspathElements(translator, symbol);
	}
}
