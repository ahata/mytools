import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public class FindScript {

	private Map<String, Object> scriptCache = new HashMap<String, Object>();

	private Map<String, Long> fileCache = new HashMap<String, Long>();

	private File scriptDir;

	private Object shell;

	private Method bindMethod;

	private Method runMethod;

	private Object emptyBind;

	/**
	 * コンストラクタ
	 * 
	 * @param scriptDir スクリプトディレクトリ
	 */
	public FindScript(File scriptDir, ClassLoader parent) throws Exception {
		this.scriptDir = scriptDir;

		Object config = Class.forName(
				"org.codehaus.groovy.control.CompilerConfiguration", true,
				parent).newInstance();
		config.getClass().getMethod("setSourceEncoding", String.class).invoke(
				config, "utf-8");
		this.shell = Class.forName("groovy.lang.GroovyShell", true, parent)
				.getConstructor(config.getClass()).newInstance(config);

		Class<?> scriptCls = Class.forName("groovy.lang.Script", true, parent);

		this.bindMethod = scriptCls.getMethod("setBinding", Class.forName(
				"groovy.lang.Binding", true, parent));
		this.runMethod = scriptCls.getMethod("run");
		this.emptyBind = Class.forName("groovy.lang.Binding", true, parent)
				.newInstance();
	}

	private Object getScript(String name) throws Exception {

		File path = new File(name);
		File script;
		if (path.isAbsolute()) {
			script = path;
		} else {
			script = new File(scriptDir, name);
		}

		if (!script.isFile()) {
			scriptCache.remove(name);
			fileCache.remove(name);
			return null;
		}
		Long lastAccess = fileCache.get(name);
		if (lastAccess == null
				|| lastAccess.longValue() < script.lastModified()) {
			// update time
			fileCache.put(name, Long.valueOf(script.lastModified()));
			// update bytes
			ByteArrayOutputStream bout = new ByteArrayOutputStream();

			InputStream in = null;
			InputStream scriptText = null;
			try {
				in = getClass().getClassLoader().getResourceAsStream("dynamic");
				scriptText = new FileInputStream(script);

				IOUtils.copy(scriptText, bout);
				bout.write('\n');
				IOUtils.copy(in, bout);

			} finally {
				IOUtils.closeQuietly(scriptText);
				IOUtils.closeQuietly(in);
			}
			Object gScript = shell.getClass().getMethod("parse",
					InputStream.class).invoke(shell,
					new ByteArrayInputStream(bout.toByteArray()));
			scriptCache.put(name, gScript);
			return gScript;
		}
		return scriptCache.get(name);
	}

	public Object run(String name, Object binding) throws Exception {
		Object script = getScript(name);
		if (script == null) {
			throw new Exception("Script not found:" + name);
		}
		if (binding == null) {
			bindMethod.invoke(script, emptyBind);
		} else {
			bindMethod.invoke(script, binding);
		}
		return runMethod.invoke(script);
	}

}
