package org.nlpcn.jcoder.run.java;

import com.google.common.base.Joiner;
import org.nlpcn.jcoder.run.CodeException;
import org.nlpcn.jcoder.scheduler.TaskException;
import org.nlpcn.jcoder.util.StaticValue;
import org.nlpcn.jcoder.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("all")
public class DynamicEngine {

	private static final Logger LOG = LoggerFactory.getLogger(DynamicEngine.class);

	private String groupName;
	private URLClassLoader classLoader;
	private String classpath;


	public DynamicEngine(String groupName) {
		this.groupName = groupName;
	}

	private DynamicEngine(URLClassLoader classLoader) {
		this.classLoader = classLoader;
		this.buildClassPath();
	}

	/**
	 * 刷新instance
	 *
	 * @throws TaskException
	 */
	public void flush(URLClassLoader classLoader) throws TaskException {
		// if class load change , to flush all task
		this.classLoader = classLoader;
		this.buildClassPath();
	}

	private void buildClassPath() {
		this.classpath = null;
		Set<String> classPathSet = new HashSet<>();

		for (URL url : this.classLoader.getURLs()) {
			try {
				String p = new File(url.toURI()).getAbsolutePath();
				classPathSet.add(p);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}

		URLClassLoader classLoader = (URLClassLoader) this.getClass().getClassLoader();

		for (URL url : classLoader.getURLs()) {
			try {
				String p = new File(url.toURI()).getAbsolutePath();
				classPathSet.add(p);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}

		this.classpath = Joiner.on(File.pathSeparator).join(classPathSet);
	}

	public Class<?> javaCodeToClass(String fullClassName, String javaCode) throws IOException, CodeException {
		Class<?> clazz = null;
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(diagnostics, null, null));
		List<JavaFileObject> jfiles = new ArrayList<JavaFileObject>();
		jfiles.add(new CharSequenceJavaFileObject(fullClassName, javaCode));

		List<String> options = new ArrayList<String>();
		options.add("-encoding");
		options.add("UTF-8");
		options.add("-classpath");
		options.add(this.classpath);
		options.add("-parameters");
        if (StaticValue.TESTRING) {
            options.add("-g");
        }

		JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, jfiles);
		boolean success = task.call();
		if (success) {
			JavaClassObject jco = fileManager.getMainJavaClassObject();
			DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(this.classLoader);
			try {
				List<JavaClassObject> innerClassJcos = fileManager.getInnerClassJavaClassObject();
				if (innerClassJcos != null && innerClassJcos.size() > 0) {
					for (JavaClassObject inner : innerClassJcos) {
						String name = inner.getName();
						name = name.substring(1, name.length() - 6).replace("/", ".");
						dynamicClassLoader.loadClass(name, inner);
					}
				}
				clazz = dynamicClassLoader.loadClass(fullClassName, jco);
			} catch (Exception e) {
				e.printStackTrace();
				LOG.error(e.getMessage(), e);
			} catch (Error e) {
				LOG.error(e.getMessage(), e);
				throw new CodeException(e.toString());
			} finally {
				if (dynamicClassLoader != null) {
					dynamicClassLoader.close();
				}
			}
		} else {
			StringBuilder error = new StringBuilder();
			for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
				error.append(compilePrint(diagnostic));
			}
			throw new CodeException(error.toString());
		}

		return clazz;
	}

	public byte[] javaCode2Bytes(String fullClassName, String javaCode) throws IOException, CodeException {
		Class<?> clazz = null;
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(diagnostics, null, null));
		List<JavaFileObject> jfiles = new ArrayList<JavaFileObject>();
		jfiles.add(new CharSequenceJavaFileObject(fullClassName, javaCode));

		List<String> options = new ArrayList<String>();

		options.add("-source");
		options.add("1.6");
		options.add("-target");
		options.add("1.6");
		options.add("-encoding");
		options.add("UTF-8");
		options.add("-classpath");
		options.add(this.classpath);
		options.add("-parameters");

		JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, jfiles);
		boolean success = task.call();
		if (success) {
			JavaClassObject jco = fileManager.getMainJavaClassObject();
			DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(this.classLoader);
			try {
				List<JavaClassObject> innerClassJcos = fileManager.getInnerClassJavaClassObject();
				if (innerClassJcos != null && innerClassJcos.size() > 0) {
					for (JavaClassObject inner : innerClassJcos) {
						String name = inner.getName();
						name = name.substring(1, name.length() - 6).replace("/", ".");
						dynamicClassLoader.loadClass(name, inner);
					}
				}
				return jco.getBytes();
			} catch (Exception e) {
				e.printStackTrace();
				LOG.error(e.getMessage(), e);
			} catch (Error e) {
				LOG.error(e.getMessage(), e);
				throw new CodeException(e.toString());
			} finally {
				if (dynamicClassLoader != null) {
					dynamicClassLoader.close();
				}
			}
		} else {
			StringBuilder error = new StringBuilder();
			for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
				error.append(compilePrint(diagnostic));
			}
			throw new CodeException(error.toString());
		}

		return null;
	}

	public <T> T javaCodeToObject(String fullClassName, String javaCode) throws IllegalAccessException, InstantiationException, IOException, CodeException {
		return (T) javaCodeToClass(fullClassName, javaCode).newInstance();
	}

	private StringBuilder compilePrint(Diagnostic<?> diagnostic) {
		StringBuilder res = new StringBuilder();
		res.append("Code:[" + diagnostic.getCode() + "]\n");
		res.append("Kind:[" + diagnostic.getKind() + "]\n");
		res.append("Position:[" + diagnostic.getPosition() + "]\n");
		res.append("Start Position:[" + diagnostic.getStartPosition() + "]\n");
		res.append("End Position:[" + diagnostic.getEndPosition() + "]\n");
		res.append("Source:[" + diagnostic.getSource() + "]\n");
		res.append("Message:[" + diagnostic.getMessage(null) + "]\n");
		res.append("LineNumber:[" + diagnostic.getLineNumber() + "]\n");
		res.append("ColumnNumber:[" + diagnostic.getColumnNumber() + "]\n");
		return res;
	}

	public <T> T javaCodeToObject(String content) throws IllegalAccessException, InstantiationException, IOException, CodeException {
		String className = new JavaSourceUtil(content).getFullName();
		if (StringUtil.isBlank(className)) {
			throw new ClassFormatError("can find class name ,please define it ! use javaCodeToObject(String fullClassName, String javaCode)");
		}
		return (T) this.javaCodeToObject(className, content);
	}

	public Class<?> javaCodeToClass(String content) throws IOException, CodeException {
		String className = new JavaSourceUtil(content).getFullName();
		if (StringUtil.isBlank(className)) {
			throw new ClassFormatError("can find class name ,please define it ! use javaCodeToObject(String fullClassName, String javaCode)");
		}
		return this.javaCodeToClass(className, content);
	}

	public URLClassLoader getClassLoader() {
		return classLoader;
	}


	public void close() throws IOException {
		if (this.classLoader != null) {
			this.classLoader.close();
			System.gc();
			try {
				Thread.sleep(300L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}