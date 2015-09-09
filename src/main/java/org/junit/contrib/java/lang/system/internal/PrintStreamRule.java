package org.junit.contrib.java.lang.system.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static java.lang.System.getProperty;

public class PrintStreamRule implements TestRule {
	private final PrintStreamHandler printStreamHandler;
	private final MuteableLogStream muteableLogStream;

	public PrintStreamRule(PrintStreamHandler printStreamHandler) {
		this.printStreamHandler = printStreamHandler;
		try {
			this.muteableLogStream = new MuteableLogStream(printStreamHandler.getStream());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				try {
					printStreamHandler.createRestoreStatement(new Statement() {
						@Override
						public void evaluate() throws Throwable {
							printStreamHandler.replaceCurrentStreamWithStream(muteableLogStream);
							base.evaluate();
						}
					}).evaluate();
				} catch (Throwable e) {
					printStreamHandler.getStream().write(muteableLogStream.getFailureLog().getBytes(Charset.defaultCharset()));
					throw e;
				}
			}
		};
	}

	public void clearLog() {
		muteableLogStream.clearLog();
	}

	public void enableLog() {
		muteableLogStream.enableLog();
	}

	public String getLog() {
		return muteableLogStream.getLog();
	}

	public String getLogWithNormalizedLineSeparator() {
		String lineSeparator = getProperty("line.separator");
		return getLog().replace(lineSeparator, "\n");
	}

	public void mute() {
		muteableLogStream.mute();
	}

	public void muteForSuccessfulTests() {
		mute();
		muteableLogStream.enableFailureLog();
	}

	private static class MuteableLogStream extends PrintStream {
		private final ByteArrayOutputStream failureLog;
		private final ByteArrayOutputStream log;
		private final MutableOutputStream muteableOriginalStream;
		private final MutableOutputStream muteableFailureLog;
		private final MutableOutputStream muteableLog;

		MuteableLogStream(OutputStream out) throws UnsupportedEncodingException {
			this(out, new ByteArrayOutputStream(), new ByteArrayOutputStream());
		}

		MuteableLogStream(OutputStream out, ByteArrayOutputStream failureLog,
				ByteArrayOutputStream log) throws UnsupportedEncodingException {
			this(new MutableOutputStream(out),
				failureLog, new MutableOutputStream(failureLog),
				log, new MutableOutputStream(log));
		}

		MuteableLogStream(MutableOutputStream muteableOriginalStream,
				ByteArrayOutputStream failureLog, MutableOutputStream muteableFailureLog,
				ByteArrayOutputStream log, MutableOutputStream muteableLog)
				throws UnsupportedEncodingException {
			super(new TeeOutputStream(
					muteableOriginalStream,
					new TeeOutputStream(muteableFailureLog, muteableLog)));
			this.failureLog = failureLog;
			this.log = log;
			this.muteableOriginalStream = muteableOriginalStream;
			this.muteableFailureLog = muteableFailureLog;
			this.muteableFailureLog.mute();
			this.muteableLog = muteableLog;
			this.muteableLog.mute();
		}

		void mute() {
			muteableOriginalStream.mute();
		}

		void clearLog() {
			log.reset();
		}

		void enableLog() {
			muteableLog.turnOutputOn();
		}

		String getLog() {
			return getLog(log);
		}

		void enableFailureLog() {
			muteableFailureLog.turnOutputOn();
		}

		String getFailureLog() {
			return getLog(failureLog);
		}

		String getLog(ByteArrayOutputStream os) {
			/* The MuteableLogStream is created with the default encoding
			 * because it writes to System.out or System.err if not muted and
			 * System.out/System.err uses the default encoding. As a result all
			 * other streams receive input that is encoded with the default
			 * encoding.
			 */
			String encoding = getProperty("file.encoding");
			try {
				return os.toString(encoding);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}

		public static class TeeOutputStream extends ProxyOutputStream {

			/** the second OutputStream to write to */
			protected OutputStream branch; //TODO consider making this private

			/**
			 * Constructs a TeeOutputStream.
			 * @param out the main OutputStream
			 * @param branch the second OutputStream
			 */
			public TeeOutputStream(final OutputStream out, final OutputStream branch) {
				super(out);
				this.branch = branch;
			}

			/**
			 * Write the bytes to both streams.
			 * @param b the bytes to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public synchronized void write(final byte[] b) throws IOException {
				super.write(b);
				this.branch.write(b);
			}

			/**
			 * Write the specified bytes to both streams.
			 * @param b the bytes to write
			 * @param off The start offset
			 * @param len The number of bytes to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
				super.write(b, off, len);
				this.branch.write(b, off, len);
			}

			/**
			 * Write a byte to both streams.
			 * @param b the byte to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public synchronized void write(final int b) throws IOException {
				super.write(b);
				this.branch.write(b);
			}

			/**
			 * Flushes both streams.
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void flush() throws IOException {
				super.flush();
				this.branch.flush();
			}

			/**
			 * Closes both output streams.
			 * 
			 * If closing the main output stream throws an exception, attempt to close the branch output stream.
			 * 
			 * If closing the main and branch output streams both throw exceptions, which exceptions is thrown by this method is
			 * currently unspecified and subject to change.
			 * 
			 * @throws IOException
			 *             if an I/O error occurs
			 */
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					this.branch.close();
				}
			}
		}

		public static class ProxyOutputStream extends FilterOutputStream {

			/**
			 * Constructs a new ProxyOutputStream.
			 * 
			 * @param proxy  the OutputStream to delegate to
			 */
			public ProxyOutputStream(final OutputStream proxy) {
				super(proxy);
				// the proxy is stored in a protected superclass variable named 'out'
			}

			/**
			 * Invokes the delegate's <code>write(int)</code> method.
			 * @param idx the byte to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void write(final int idx) throws IOException {
				try {
					beforeWrite(1);
					out.write(idx);
					afterWrite(1);
				} catch (final IOException e) {
					handleIOException(e);
				}
			}

			/**
			 * Invokes the delegate's <code>write(byte[])</code> method.
			 * @param bts the bytes to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void write(final byte[] bts) throws IOException {
				try {
					final int len = bts != null ? bts.length : 0;
					beforeWrite(len);
					out.write(bts);
					afterWrite(len);
				} catch (final IOException e) {
					handleIOException(e);
				}
			}

			/**
			 * Invokes the delegate's <code>write(byte[])</code> method.
			 * @param bts the bytes to write
			 * @param st The start offset
			 * @param end The number of bytes to write
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void write(final byte[] bts, final int st, final int end) throws IOException {
				try {
					beforeWrite(end);
					out.write(bts, st, end);
					afterWrite(end);
				} catch (final IOException e) {
					handleIOException(e);
				}
			}

			/**
			 * Invokes the delegate's <code>flush()</code> method.
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void flush() throws IOException {
				try {
					out.flush();
				} catch (final IOException e) {
					handleIOException(e);
				}
			}

			/**
			 * Invokes the delegate's <code>close()</code> method.
			 * @throws IOException if an I/O error occurs
			 */
			@Override
			public void close() throws IOException {
				try {
					out.close();
				} catch (final IOException e) {
					handleIOException(e);
				}
			}

			/**
			 * Invoked by the write methods before the call is proxied. The number
			 * of bytes to be written (1 for the {@link #write(int)} method, buffer
			 * length for {@link #write(byte[])}, etc.) is given as an argument.
			 * <p>
			 * Subclasses can override this method to add common pre-processing
			 * functionality without having to override all the write methods.
			 * The default implementation does nothing.
			 *
			 * @since 2.0
			 * @param n number of bytes to be written
			 * @throws IOException if the pre-processing fails
			 */
			protected void beforeWrite(final int n) throws IOException {
			}

			/**
			 * Invoked by the write methods after the proxied call has returned
			 * successfully. The number of bytes written (1 for the
			 * {@link #write(int)} method, buffer length for {@link #write(byte[])},
			 * etc.) is given as an argument.
			 * <p>
			 * Subclasses can override this method to add common post-processing
			 * functionality without having to override all the write methods.
			 * The default implementation does nothing.
			 *
			 * @since 2.0
			 * @param n number of bytes written
			 * @throws IOException if the post-processing fails
			 */
			protected void afterWrite(final int n) throws IOException {
			}

			/**
			 * Handle any IOExceptions thrown.
			 * <p>
			 * This method provides a point to implement custom exception
			 * handling. The default behaviour is to re-throw the exception.
			 * @param e The IOException thrown
			 * @throws IOException if an I/O error occurs
			 * @since 2.0
			 */
			protected void handleIOException(final IOException e) throws IOException {
				throw e;
			}

		}
	}

	private static class MutableOutputStream extends OutputStream {
		private final OutputStream originalStream;
		private boolean mute = false;

		MutableOutputStream(OutputStream originalStream) {
			this.originalStream = originalStream;
		}

		void mute() {
			mute = true;
		}

		void turnOutputOn() {
			mute = false;
		}

		@Override
		public void write(int b) throws IOException {
			if (!mute)
				originalStream.write(b);
		}
	}
}
