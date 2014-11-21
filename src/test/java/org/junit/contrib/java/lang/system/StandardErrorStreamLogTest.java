package org.junit.contrib.java.lang.system;

import static java.lang.System.err;
import static java.lang.System.setErr;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(HierarchicalContextRunner.class)
public class StandardErrorStreamLogTest {
	private static final String ARBITRARY_TEXT = "arbitrary text";

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	public class ForEveryLogMode {
		//use log with standard mode as representative for every log mode
		private final StandardErrorStreamLog log = new StandardErrorStreamLog();

		@Test
		public void logWriting() throws Throwable {
			executeRuleWithStatement(log, new WriteTextToStandardErrorStream());
			assertThat(log.getLog(), is(equalTo(ARBITRARY_TEXT)));
		}

		@Test
		public void restoreSystemErrorStream() throws Throwable {
			PrintStream originalStream = err;
			executeRuleWithStatement(log, new WriteTextToStandardErrorStream());
			assertThat(originalStream, is(sameInstance(err)));
		}

		@Test
		public void collectsLogAfterClearing() throws Throwable {
			executeRuleWithStatement(log, new ClearLogWhileWritingTextToStandardErrorStream(log));
			assertThat(log.getLog(), is(equalTo(ARBITRARY_TEXT)));
		}
	}

	@Test
	public void stillWritesToSystemErrorStreamIfNoLogModeHasBeenSpecified() throws Throwable {
		StandardErrorStreamLog log = new StandardErrorStreamLog();
		PrintStream originalStream = err;
		try {
			ByteArrayOutputStream captureErrorStream = new ByteArrayOutputStream();
			setErr(new PrintStream(captureErrorStream));
			executeRuleWithStatement(log, new WriteTextToStandardErrorStream());
			assertThat(captureErrorStream, hasToString(equalTo(ARBITRARY_TEXT)));
		} finally {
			setErr(originalStream);
		}
	}

	@Test
	public void doesNotWriteToSystemErrorStreamForLogOnlyMode() throws Throwable {
		StandardErrorStreamLog log = new StandardErrorStreamLog(LogMode.LOG_ONLY);
		PrintStream originalStream = err;
		try {
			ByteArrayOutputStream captureErrorStream = new ByteArrayOutputStream();
			setErr(new PrintStream(captureErrorStream));
			executeRuleWithStatement(log, new WriteTextToStandardErrorStream());
			assertThat(captureErrorStream, hasToString(isEmptyString()));
		} finally {
			setErr(originalStream);
		}
	}

	@Test
	public void cannotBeCreatedWithoutLogMode() {
		thrown.expect(NullPointerException.class);
		thrown.expectMessage(equalTo("The LogMode is missing."));
		new StandardErrorStreamLog(null);
	}

	private void executeRuleWithStatement(TestRule rule, Statement statement) throws Throwable {
		rule.apply(statement, null).evaluate();
	}

	private class WriteTextToStandardErrorStream extends Statement {
		@Override
		public void evaluate() throws Throwable {
			err.print(ARBITRARY_TEXT);
		}
	}

	private class ClearLogWhileWritingTextToStandardErrorStream extends Statement {
		private final StandardErrorStreamLog log;

		private ClearLogWhileWritingTextToStandardErrorStream(StandardErrorStreamLog log) {
			this.log = log;
		}

		@Override
		public void evaluate() throws Throwable {
			err.print(ARBITRARY_TEXT);
			log.clear();
			err.print(ARBITRARY_TEXT);
		}
	}
}
