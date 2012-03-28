import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.Selection;

@SuppressWarnings("unchecked")
public class ScriptManager extends JPanel {

	private static final long serialVersionUID = 1L;

	private static File setting;

	private static File scriptsDir;

	/** Groovy Class Loader */
	private static ClassLoader groovyClassLoader;

	private static FindScript engine;

	private static Logger logger;

	private static Class<?> bindingCls;

	private static Class<?> groovyClassLoaderCls;

	private static List<Class<?>> categories = new ArrayList<Class<?>>();

	private ScriptManager() {
		// can not be called
	}

	static {
		// init directories
		setting = new File(jEdit.getSettingsDirectory(), Constants.settingDir);
		File resourceDir = new File(setting, Constants.resourceDir);
		File classDir = new File(setting, Constants.classesDir);
		File lib = new File(setting, Constants.libDir);
		scriptsDir = new File(setting, Constants.scriptsDir);
		checkDirs(new File[] { setting, lib, resourceDir, classDir, scriptsDir });

		try {
			logger = new Logger(new File(setting, "log.txt"));

			logger.log("static init block start");
			File startScript = new File(scriptsDir, Constants.startupShellName);

			if (!startScript.exists()) {
				FileUtils.copyURLToFile(ScriptManager.class.getClassLoader()
						.getResource(startScript.getName()), startScript);
			}

			List<URL> urls = new ArrayList<URL>();
			// リソースファイルのディレクトリ
			urls.add(resourceDir.toURI().toURL());
			// classesディレクトリ
			urls.add(classDir.toURI().toURL());
			// lib配下のjarをclasspathに追加する
			for (File f : lib.listFiles()) {
				if (f.getName().toLowerCase().endsWith(".jar")) {
					urls.add(f.toURI().toURL());
				}
			}

			File classpathLink = new File(setting, Constants.classPathFileName);
			if (!classpathLink.exists()) {
				classpathLink.createNewFile();
			} else {
				// LINK ファイル
				List<String> lines = FileUtils.readLines(classpathLink,
						Constants.encoding);
				for (String s : lines) {
					File entry = new File(s);
					if (entry.exists()) {
						urls.add(entry.toURI().toURL());
					}
				}
			}

			initScriptClassLoader(urls);

			logger.log("class loader initialized");

			initScriptEngineAndMethod(scriptsDir);

			initCategories();

			logger.log("static init block end");

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void initScriptClassLoader(List<URL> urls) throws Exception {
		// init class loader -----------------------------
		ClassLoader urlLoader = new URLClassLoader(urls.toArray(new URL[] {}),
				ScriptManager.class.getClassLoader());

		Class<?> configCls = Class.forName(
				"org.codehaus.groovy.control.CompilerConfiguration", true,
				urlLoader);

		Object config = configCls.newInstance();
		configCls.getMethod("setSourceEncoding", String.class).invoke(config,
				"utf-8");
		groovyClassLoaderCls = Class.forName("groovy.lang.GroovyClassLoader",
				true, urlLoader);

		groovyClassLoader = (ClassLoader) groovyClassLoaderCls.getConstructor(
				ClassLoader.class, configCls).newInstance(urlLoader, config);
		// -----------------------------------------------
	}

	private static void initCategories() throws Exception {
		File categoryDir = new File(scriptsDir, "categories");
		categoryDir.mkdir();
		Method m = groovyClassLoaderCls.getMethod("parseClass", File.class);
		for (File f : categoryDir.listFiles()) {
			if (f.isFile()) {
				categories.add((Class<?>) m.invoke(groovyClassLoader, f));
			}
		}
	}

	private static void initScriptEngineAndMethod(File scriptsDir)
			throws Exception {
		bindingCls = Class.forName("groovy.lang.Binding", true,
				groovyClassLoader);

		engine = new FindScript(scriptsDir, groovyClassLoader);
	}

	private static void checkDirs(File[] dirs) {
		for (File dir : dirs) {
			if (!dir.exists()) {
				dir.mkdir();
			}
		}
	}

	public static void ruby() {
		// スクリプトの標準出力を持つインスタンス
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(bout);
		PrintStream stdOut = System.out;
		PrintStream stdErr = System.err;

		try {
			System.setOut(out);
			System.setErr(out);

			Buffer buffer = jEdit.getActiveView().getBuffer();
			String script = buffer.getText(0, buffer.getLength());

			Map<String, Object> vars = new HashMap<String, Object>();
			vars.put(Constants.refOfSettingDir, setting);
			vars.put("args", new String[] { script });
			Object binding = bindingCls.getConstructor(Map.class).newInstance(
					vars);
			engine.run("_ruby", binding);
		} catch (Exception e) {
			StringBuffer buf = new StringBuffer();
			// エラーの前に、outputをまず表示する。
			buf.append(bout.toString());
			if (buf.length() > 0) {
				buf.append("\n");
			}

			// エラーメッセージを表示する。
			Throwable th = e;
			Throwable cause = null;
			while (th != null) {
				cause = th;
				th = th.getCause();
			}
			// 例外のスタックとレース
			buf.append(ExceptionUtils.getStackTrace(cause));
			show(buf.toString());
		} finally {
			System.setOut(stdOut);
			System.setErr(stdErr);
		}
	}

	public static void execute() {

		// スクリプトの標準出力を持つインスタンス
		// StringWriter out = new StringWriter();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(bout);
		PrintStream stdOut = System.out;
		PrintStream stdErr = System.err;

		try {
			System.setOut(out);
			System.setErr(out);

			Map<String, Object> vars = new HashMap<String, Object>();
			vars.put("out", out);
			vars.put(Constants.refOfSettingDir, setting);
			vars.put(Constants.refOfGroovyEngin, engine);
			vars.put("_categories_", categories);
			vars.put("_textArea", jEdit.getActiveView().getTextArea());

			// 操作バッファーを取得する
			Buffer buffer = jEdit.getActiveView().getBuffer();
			vars.put("_buffer", buffer);

			// インプットストリングの区切りをを取得する
			String seper = buffer.getLineText(0);
			int startLine;
			if (seper == null || !seper.matches("-+")) {
				// スクリプトだけです。
				startLine = 0;
			} else {
				// インプットがあります。
				startLine = 1;
			}

			List<String> input = new ArrayList<String>();
			int varIndex = 1;
			// 第一行から
			for (int i = startLine; i < buffer.getLineCount(); i++) {
				if (startLine == 1 && buffer.getLineText(i).equals(seper)) {
					List<String> var = new ArrayList<String>();
					var.addAll(input);
					input.clear();
					vars.put(Constants.txtVarPrefix + varIndex++, var);
				} else {
					input.add(buffer.getLineText(i));
				}
			}
			vars.put(Constants.refOfScriptSrc, StringUtils.join(input, "\n"));

			Object binding = bindingCls.getConstructor(Map.class).newInstance(
					vars);

			engine.run(Constants.startupShellName, binding);

			show(bout.toString());
		} catch (Exception e) {
			StringBuffer buf = new StringBuffer();
			// エラーの前に、outputをまず表示する。
			buf.append(bout.toString());
			if (buf.length() > 0) {
				buf.append("\n");
			}

			// エラーメッセージを表示する。
			Throwable th = e;
			Throwable cause = null;
			while (th != null) {
				cause = th;
				th = th.getCause();
			}
			// 例外のスタックとレース
			buf.append(ExceptionUtils.getStackTrace(cause));
			show(buf.toString());
		} finally {
			System.setOut(stdOut);
			System.setErr(stdErr);
		}
	}

	private static void show(final String msg) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// 結果出力
				Buffer output = jEdit.newFile(jEdit.getActiveView());
				output.remove(0, output.getLength());
				output.insert(0, msg.replaceAll("\r", ""));
				output.setDirty(false);
				output.setReadOnly(false);
			}
		});
	}

	private static int findFirstContentLine(String seper) {
		Buffer buffer = jEdit.getActiveView().getBuffer();
		for (int i = 1; i < buffer.getLineCount(); i++) {
			if (!buffer.getLineText(i).equals(seper)) {
				return i;
			}
		}
		return -1;
	}

	public static void select() {
		// 操作バッファーを取得する
		Buffer buffer = jEdit.getActiveView().getBuffer();

		// インプットストリングの区切りをを取得する
		String seper = buffer.getLineText(0);
		if (!seper.matches("-+")) {
			return;
		}

		JEditTextArea textArea = jEdit.getActiveView().getTextArea();

		// lines.length is always greater than 1
		int[] lines = textArea.getSelectedLines();
		boolean isMultiSelect = !ArrayUtils.isEmpty(lines)
				&& (lines.length != lines[lines.length - 1] - lines[0] + 1);

		int start = findFirstContentLine(seper);
		if (start == -1) {
			return;
		}

		if (!isMultiSelect
				&& StringUtils.isNotEmpty(textArea.getSelectedText())) {
			OUT: for (int i = lines[lines.length - 1] + 1; i < buffer
					.getLineCount(); i++) {
				if (seper.equals(buffer.getLineText(i))) {
					for (int index = i + 1; index < buffer.getLineCount(); index++) {
						if (!buffer.getLineText(index).equals(seper)) {
							start = index;
							break OUT;
						}
					}
					break;
				}
			}
		}

		int end = start;
		while (end < buffer.getLineCount()
				&& !buffer.getLineText(end).equals(seper)) {
			end++;
		}

		if (start <= end - 1) {
			selectLines(start, end - 1);
		}
	}

	public static void selectCurrent() {
		// 操作バッファーを取得する
		Buffer buffer = jEdit.getActiveView().getBuffer();

		// インプットストリングの区切りをを取得する
		String seper = buffer.getLineText(0);
		if (!seper.matches("-+")) {
			return;
		}

		JEditTextArea textArea = jEdit.getActiveView().getTextArea();

		int start = textArea.getCaretLine();
		int end = start;
		while (start >= 0 && !buffer.getLineText(start).equals(seper)) {
			start--;
		}
		int max = buffer.getLineCount();
		while (end < max && !buffer.getLineText(end).equals(seper)) {
			end++;
		}
		selectLines(start + 1, end - 1);
	}

	private static void selectLines(int start, int end) {
		if (start > end) {
			return;
		}

		Buffer buffer = jEdit.getActiveView().getBuffer();
		Selection selection = new Selection.Range(
				buffer.getLineStartOffset(start),
				buffer.getLineEndOffset(end) - 1);

		jEdit.getActiveView().getTextArea().setSelection(selection);
	}

	static void debug(String msg) {
		JOptionPane.showMessageDialog(null, msg);
	}
}
