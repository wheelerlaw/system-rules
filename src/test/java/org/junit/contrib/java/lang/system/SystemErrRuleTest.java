package org.junit.contrib.java.lang.system;

import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.setErr;
import static java.lang.System.setProperty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

public class SystemErrRuleTest {
	private final PrintStream originalErr = err;

	@Rule
	public TestRule restoreSystemProperties = new RestoreSystemProperties();

	@After
	public void restoreSystemErr() {
		setErr(originalErr);
	}

	@Test
	public void restoresSystemErr() throws Throwable {
		SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				PrintStream otherErr = new PrintStream(
					new ByteArrayOutputStream());
				setErr(otherErr);
			}
		});
		assertThat(err, is(sameInstance(originalErr)));
	}

	@Test
	public void doesNotMuteSystemErrByDefault() throws Throwable {
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(systemErr, hasToString(equalTo("arbitrary text")));
	}

	@Test
	public void doesNotWriteToSystemErrIfMutedGlobally() throws Throwable {
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		SystemErrRule rule = new SystemErrRule().mute();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(systemErr, hasToString(isEmptyString()));
	}

	@Test
	public void doesNotWriteToSystemErrIfMutedLocally() throws Throwable {
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		final SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				rule.mute();
				err.print("arbitrary text");
			}
		});
		assertThat(systemErr, hasToString(isEmptyString()));
	}

	@Test
	public void doesNotWriteToSystemErrForSuccessfulTestIfMutedGloballyForSuccessfulTests()
			throws Throwable{
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		SystemErrRule rule = new SystemErrRule().muteForSuccessfulTests();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(systemErr, hasToString(isEmptyString()));
	}

	@Test
	public void writesToSystemErrForFailingTestIfMutedGloballyForSuccessfulTests()
			throws Throwable {
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		SystemErrRule rule = new SystemErrRule().muteForSuccessfulTests();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				err.print("arbitrary text");
				fail();
			}
		});
		assertThat(systemErr, hasToString("arbitrary text"));
	}

	@Test
	public void doesNotWriteToSystemErrForSuccessfulTestIfMutedLocallyForSuccessfulTests()
			throws Throwable{
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		final SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				rule.muteForSuccessfulTests();
				err.print("arbitrary text");
			}
		});
		assertThat(systemErr, hasToString(isEmptyString()));
	}

	@Test
	public void writesToSystemErrForFailingTestIfMutedLocallyForSuccessfulTests()
			throws Throwable{
		ByteArrayOutputStream systemErr = useReadableSystemErr();
		final SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				rule.muteForSuccessfulTests();
				err.print("arbitrary text");
				fail();
			}
		});
		assertThat(systemErr, hasToString("arbitrary text"));
	}

	@Test
	public void doesNotLogByDefault() throws Throwable {
		SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(rule.getLog(), isEmptyString());
	}

	@Test
	public void logsIfEnabledGlobally() throws Throwable {
		SystemErrRule rule = new SystemErrRule().enableLog();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(rule.getLog(), is(equalTo("arbitrary text")));
	}

	@Test
	public void logsIfEnabledLocally() throws Throwable {
		final SystemErrRule rule = new SystemErrRule();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				rule.enableLog();
				err.print("arbitrary text");
			}
		});
		assertThat(rule.getLog(), is(equalTo("arbitrary text")));
	}

	@Test
	public void collectsLogAfterClearing() throws Throwable {
		final SystemErrRule rule = new SystemErrRule().enableLog();
		executeRuleWithStatement(rule, new Statement() {
			@Override
			public void evaluate() throws Throwable {
				err.print("text that is cleared");
				rule.clearLog();
				err.print("arbitrary text");
			}
		});
		assertThat(rule.getLog(), is(equalTo("arbitrary text")));
	}

	@Test
	public void logsIfMuted() throws Throwable {
		SystemErrRule rule = new SystemErrRule().enableLog().mute();
		executeRuleWithStatement(rule, writeTextToSystemErr("arbitrary text"));
		assertThat(rule.getLog(), is(equalTo("arbitrary text")));
	}

	@Test
	public void providesLogWithNormalizedNewLineCharacters() throws Throwable {
		setProperty("line.separator", "\r\n");
		SystemErrRule rule = new SystemErrRule().enableLog();
		executeRuleWithStatement(rule, writeTextToSystemErr(format("arbitrary%ntext%n")));
		assertThat(rule.getLogWithNormalizedLineSeparator(), is(equalTo("arbitrary\ntext\n")));
	}

	private ByteArrayOutputStream useReadableSystemErr() {
		ByteArrayOutputStream readableStream = new ByteArrayOutputStream();
		setErr(new PrintStream(readableStream));
		return readableStream;
	}

	private Statement writeTextToSystemErr(final String text) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				err.print(text);
			}
		};
	}

	private void executeRuleWithStatement(TestRule rule, Statement statement)
			throws Throwable {
		try {
			rule.apply(statement, null).evaluate();
		} catch (AssertionError ignored) {
			//we must ignore the exception in case of a failing statement.
		}
	}
}
