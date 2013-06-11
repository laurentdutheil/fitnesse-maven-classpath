package fitnesse.wikitext.widgets;

public class MavenClasspathExtractionException extends RuntimeException {

	/** */
	private static final long serialVersionUID = 7399943859824876908L;

	public MavenClasspathExtractionException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public MavenClasspathExtractionException(final Throwable cause) {
		super(cause);
	}
}
