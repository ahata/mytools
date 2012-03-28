import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;

public class Logger {
	private PrintWriter m_writer;

	public Logger(File file) {
		try {
			m_writer = new PrintWriter(new OutputStreamWriter(
					new FileOutputStream(file), "utf-8"), true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void log(String message) {
		m_writer.println(System.currentTimeMillis() + " " + message);
	}

	public void close() {
		IOUtils.closeQuietly(m_writer);
	}
}
