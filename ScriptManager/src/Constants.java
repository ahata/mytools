import java.lang.reflect.Field;
import java.util.ResourceBundle;

public class Constants {

	public static String settingDir;

	public static String resourceDir;

	public static String classesDir;

	public static String libDir;

	public static String scriptsDir;

	public static String startupShellName;

	public static String classPathFileName;

	public static String encoding;

	public static String refOfSettingDir;

	public static String refOfGroovyEngin;

	public static String txtVarPrefix;

	public static String refOfScriptSrc;

	static {
		ResourceBundle bundle = ResourceBundle.getBundle("constants");
		for (Field f : Constants.class.getDeclaredFields()) {
			try {
				f.set(null, bundle.getString(f.getName()));
				System.out.println(f);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
