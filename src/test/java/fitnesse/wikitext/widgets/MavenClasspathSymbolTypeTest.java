package fitnesse.wikitext.widgets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import util.Maybe;
import fitnesse.wikitext.parser.Parser;
import fitnesse.wikitext.parser.Symbol;
import fitnesse.wikitext.parser.SymbolType;
import fitnesse.wikitext.parser.Translator;

public class MavenClasspathSymbolTypeTest {

	private MavenClasspathSymbolType mavenClasspathSymbolType;
	private MavenClasspathExtractor mavenClasspathExtractor;
	private Symbol symbol;
	private Parser parser;
	private Translator translator;

	@Before
	public void setUp() throws Exception {
		symbol = mock(Symbol.class);
		parser = mock(Parser.class);
		translator = mock(Translator.class);
		mavenClasspathExtractor = mock(MavenClasspathExtractor.class);

		mavenClasspathSymbolType = new MavenClasspathSymbolType();
		mavenClasspathSymbolType.setMavenClasspathExtractor(mavenClasspathExtractor);
	}

	@Test
	public void canParseAProperDirective() {
		when(parser.moveNext(1)).thenReturn(new Symbol(SymbolType.Whitespace));
		final Symbol expectedSymbol = new Symbol(SymbolType.Text, "thePomFile");
		when(parser.parseTo(SymbolType.EndCell)).thenReturn(expectedSymbol);

		final Maybe<Symbol> result = mavenClasspathSymbolType.parse(symbol, parser);
		assertNotNull(result);
		assertNotSame(Symbol.nothing, result);
	}

	@Test
	public void translatesToClasspathEntries() {
		when(translator.translateTree(symbol)).thenReturn("thePomFile");

		when(mavenClasspathExtractor.extractClasspathEntries(any(ExtractClasspathEntriesParameter.class))).thenReturn(
				Arrays.asList("test1", "test2"));

		assertEquals(
				"scope: test<br/><span class=\"meta\">classpath: test1</span><br/><span class=\"meta\">classpath: test2</span><br/>",
				mavenClasspathSymbolType.toTarget(translator, symbol));
	}

	@Test
	public void translatesToJavaClasspath() {
		when(translator.translateTree(symbol)).thenReturn("thePomFile");

		when(mavenClasspathExtractor.extractClasspathEntries(any(ExtractClasspathEntriesParameter.class))).thenReturn(
				Arrays.asList("test1", "test2"));

		assertArrayEquals(new Object[] { "test1", "test2" }, mavenClasspathSymbolType.providePaths(translator, symbol)
				.toArray());
	}

}
