package org.nlpcn.jcoder.controller;

import com.google.common.collect.ImmutableMap;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.nlpcn.jcoder.constant.Constants;
import org.nlpcn.jcoder.domain.FileInfo;
import org.nlpcn.jcoder.domain.Task;
import org.nlpcn.jcoder.filter.AuthoritiesManager;
import org.nlpcn.jcoder.run.java.JavaSourceUtil;
import org.nlpcn.jcoder.service.GroupService;
import org.nlpcn.jcoder.service.JarService;
import org.nlpcn.jcoder.service.ProxyService;
import org.nlpcn.jcoder.util.IOUtil;
import org.nlpcn.jcoder.util.Restful;
import org.nlpcn.jcoder.util.StaticValue;
import org.nlpcn.jcoder.util.StringUtil;
import org.nutz.dao.Cnd;
import org.nutz.http.Response;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.lang.Streams;
import org.nutz.mvc.Mvcs;
import org.nutz.mvc.annotation.AdaptBy;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.By;
import org.nutz.mvc.annotation.Filters;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.Param;
import org.nutz.mvc.upload.TempFile;
import org.nutz.mvc.upload.UploadAdaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by Ansj on 19/12/2017.
 * 这个类提供集群间文件交换下载
 */

@IocBean
@Filters(@By(type = AuthoritiesManager.class))
@At("/admin/fileInfo")
@Ok("json")
public class FileInfoAction {

	private static final Logger LOG = LoggerFactory.getLogger(FileInfoAction.class);


	@Inject
	private ProxyService proxyService;

	@Inject
	private GroupService groupService;

	/**
	 * 获取文件列表
	 */
	@At
	public Restful listFiles(@Param("hostPort") String hostPort, @Param("groupName") String groupName) throws Exception {

		if (Constants.HOST_MASTER.equals(hostPort)) { //说明是主机
			hostPort = StaticValue.space().getRandomCurrentHostPort(groupName);
			if (hostPort == null) {
				return Restful.fail().msg("无同步主机");
			}
		}

		if (StringUtil.isBlank(hostPort) || StaticValue.getHostPort().equals(hostPort)) {
			List<FileInfo> result = new ArrayList<>();

			Path path = new File(StaticValue.GROUP_FILE, groupName).toPath();

			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				// 在访问子目录前触发该方法
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					File file = dir.toFile();
					if (!file.canRead() || file.isHidden() || file.getName().charAt(0) == '.') {
						LOG.warn(path.toString() + " is hidden or can not read or start whth '.' so skip it ");
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					File file = path.toFile();
					if (!file.canRead() || file.isHidden() || file.getName().charAt(0) == '.') {
						LOG.warn(path.toString() + " is hidden or can not read or start whth '.' so skip it ");
						return FileVisitResult.CONTINUE;
					}
					try {
						result.add(new FileInfo(file));
					} catch (Exception e) {
						e.printStackTrace();
					}
					return FileVisitResult.CONTINUE;
				}
			});

			return Restful.instance().obj(result);
		} else {
			Response response = proxyService.post(hostPort, "/admin/fileInfo/listFiles", ImmutableMap.of("groupName", groupName), 10000);

			if (response.isOK()) {
				return JSONObject.parseObject(response.getContent(), Restful.class);
			} else {
				return Restful.fail().msg(response.getContent());
			}
		}


	}

	/**
	 * 获取文件目录树
	 */
	@At
	public Restful getFileTree(@Param("hostPort") String hostPort, @Param("groupName") String groupName) throws Exception {
		JSONArray nodes = new JSONArray();
		if (Constants.HOST_MASTER.equals(hostPort)) { //说明是主机
			hostPort = StaticValue.space().getRandomCurrentHostPort(groupName);
			if (hostPort == null) {
				return Restful.fail().msg("无同步主机");
			}
		}

		if (StringUtil.isBlank(hostPort) || StaticValue.getHostPort().equals(hostPort)) {
			List<FileInfo> result = new ArrayList<>();

			Path path = new File(StaticValue.GROUP_FILE, groupName).toPath();
			Map<String, String> map = new HashMap<String, String>();
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				// 在访问子目录前触发该方法
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					File file = dir.toFile();
					if (!file.canRead() || file.isHidden() || file.getName().charAt(0) == '.') {
						LOG.warn(path.toString() + " is hidden or can not read or start whth '.' so skip it ");
						return FileVisitResult.SKIP_SUBTREE;
					}
					JSONObject jsonObject = new JSONObject();
					jsonObject.put("name", file.getName());
					UUID uuid = UUID.randomUUID();
					map.put(file.getName(), file.getName().equals(groupName) ? "0" : uuid.toString());
					jsonObject.put("id", map.get(file.getName()));
					jsonObject.put("open", true);
					jsonObject.put("isParent", true);
					if (!file.getName().equals(groupName)) {
						jsonObject.put("pId", map.get(file.getParentFile().getName()));
					}
					FileInfo fileInfo = new FileInfo(file);
					JSONObject fi = JSONObject.parseObject(JSONObject.toJSONString(fileInfo));
					fi.put("date", fileInfo.lastModified());
					jsonObject.put("file", fi);
					nodes.add(jsonObject);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					File file = path.toFile();
					if (!file.canRead() || file.isHidden() || file.getName().charAt(0) == '.') {
						LOG.warn(path.toString() + " is hidden or can not read or start whth '.' so skip it ");
						return FileVisitResult.CONTINUE;
					}
					try {
						result.add(new FileInfo(file));
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("name", file.getName());
						UUID uuid = UUID.randomUUID();
						map.put(file.getName(), uuid.toString());
						jsonObject.put("id", uuid.toString());
						jsonObject.put("open", true);
						jsonObject.put("pId", map.get(file.getParentFile().getName()));
						FileInfo fileInfo = new FileInfo(file);
						JSONObject fi = JSONObject.parseObject(JSONObject.toJSONString(fileInfo));
						fi.put("date", fileInfo.lastModified());
						jsonObject.put("file", fi);
						nodes.add(jsonObject);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return FileVisitResult.CONTINUE;
				}
			});
			return Restful.instance().obj(nodes);
		} else {
			Response response = proxyService.post(hostPort, "/admin/fileInfo/getFileTree", ImmutableMap.of("groupName", groupName), 10000);

			if (response.isOK()) {
				return JSONObject.parseObject(response.getContent(), Restful.class);
			} else {
				return Restful.fail().msg(response.getContent());
			}
		}
	}


	/**
	 * 获得文件的正文
	 */
	@At
	@Ok("void")
	public Restful fileContent(@Param("hostPort") String hostPort, @Param("groupName") String groupName, @Param("relativePath") String relativePath, @Param(value = "maxSize", df = "20480") int maxSize) throws Exception {
		if (Constants.HOST_MASTER.equals(hostPort)) { //说明是主机
			hostPort = StaticValue.space().getRandomCurrentHostPort(groupName);
			if (hostPort == null) {
				return Restful.fail().msg("无同步主机");
			}
		}
		if (StringUtil.isBlank(hostPort) || StaticValue.getHostPort().equals(hostPort)) {
			File file = new File(StaticValue.GROUP_FILE, groupName + relativePath);
			if (!file.exists()) {
				return Restful.fail().msg("文件不存在");//obj是空
			}

			if (file.isDirectory()) {
				return Restful.fail().msg(relativePath + " 是目录");
			}

			byte[] bytes = new byte[maxSize];

			try (FileInputStream fis = new FileInputStream(file)) {
				int len = fis.read(bytes);
				String content = "";
				if (len > 0) {
					content = new String(bytes, 0, len);
				}
				return Restful.ok().msg(content).obj(new FileInfo(file));
			}
		} else {
			Response post = proxyService.post(hostPort, "/admin/fileInfo/fileContent", ImmutableMap.of("hostPort", hostPort, "groupName", groupName, "relativePath", relativePath, "maxSize", maxSize), 10000);

			return Restful.instance(post);
		}

	}

	/**
	 * 获得一个文件的输出流
	 *
	 * @param relativePath 抽象路径
	 */
	@At
	@Ok("void")
	public void downFile(@Param("hostPort") String hostPort, @Param("groupName") String groupName, @Param("relativePath") String relativePath, HttpServletResponse response) throws Throwable {

		if (Constants.HOST_MASTER.equals(hostPort)) { //说明是主机
			hostPort = StaticValue.space().getRandomCurrentHostPort(groupName);
			if (hostPort == null) {
				throw new RuntimeException("无同步主机");
			}
		}


		if (StringUtil.isBlank(hostPort) || StaticValue.getHostPort().equals(hostPort)) {
			if (relativePath.contains("..")) {
				throw new FileNotFoundException("下载路径不能包含`..`字符");
			}

			File file = new File(StaticValue.GROUP_FILE, groupName + relativePath);

			if (!file.exists()) {
				response.setStatus(404);
				response.getWriter().write(Restful.fail().msg("fail not found exception").code(404).toJsonString());
				return;

			}

			response.setContentType("application/octet-stream");

			if (file.isDirectory()) {
				response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(file.getName(), "utf-8") + ".zip");
				try (ZipOutputStream out = new ZipOutputStream(response.getOutputStream())) {
					Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {

						@Override
						public FileVisitResult visitFile(Path tempFile, BasicFileAttributes attrs) throws IOException {
							File f = tempFile.toFile();
							out.putNextEntry(new ZipEntry(f.getName()));
							int len = 0;
							byte[] buffer = new byte[10240];
							if (!f.isDirectory() && f.canRead()) {
								try (FileInputStream fis = new FileInputStream(f)) {
									BufferedInputStream bis = new BufferedInputStream(fis, 1024 * 10);
									while ((len = bis.read(buffer, 0, 1024 * 10)) != -1) {
										out.write(buffer, 0, len);
									}
								}
							}
							return FileVisitResult.CONTINUE;
						}
					});
				}
			} else {
				response.setContentLength((int) file.length());
				response.addHeader("Content-Disposition", "attachment;filename=" + file.getName());
				ServletOutputStream outputStream = response.getOutputStream();

				try (FileInputStream fis = new FileInputStream(file)) {
					Streams.write(outputStream, fis);
				}
			}
		} else {
			Response post = proxyService.post(hostPort, "/admin/fileInfo/downFile", ImmutableMap.of("hostPort", hostPort, "groupName", groupName, "relativePath", relativePath), 100000);
			IOUtil.writeAndClose(post, response);
		}

	}

	/**
	 * 删除一个文件或文件夹
	 */
	@At
	public Restful delete(@Param("groupName") String groupName, @Param("relativePath") String relativePath) {
		if (relativePath.contains("..")) {
			return Restful.instance(false, "删除路径不能包含`..`字符");
		}
		File file = new File(StaticValue.GROUP_FILE, groupName + relativePath);
		if (file.isDirectory()) {
			org.nutz.lang.Files.deleteDir(file);
		} else {
			org.nutz.lang.Files.deleteFile(file);
		}
		return Restful.ok();
	}

	/**
	 * 删除一个文件或文件夹
	 */
	@At
	public Restful deleteFile(@Param("hostPort[]") String[] hostPorts,@Param("groupName") String groupName,
							  @Param("relativePaths[]") String[] relativePaths,
							  @Param(value = "first", df = "true") boolean first) throws Exception {
		try {
			if(!first){
				//String[] paths = relativePath.split(",");
				StringBuilder sb = new StringBuilder();
				for(int i = 0;i < relativePaths.length;i++){
					if (relativePaths[i].contains("..")) {
						return Restful.instance(false, "删除路径不能包含`..`字符");
					}
					if(relativePaths[i].endsWith(".jar") && relativePaths[i].contains("lib") && !relativePaths[i].contains("target")){
						JarService jarService = JarService.getOrCreate(groupName) ;
						File file = new File(StaticValue.GROUP_FILE, groupName + relativePaths[i]);
						jarService.removeJar(file);
						if(file.exists()){
							sb.append("文件："+file.getName()+"删除失败！,");
						}else{
							sb.append("文件："+file.getName()+"删除成功！,");
						}
						continue;
					}else if(relativePaths[i].contains("target")){
					    sb.append("文件："+relativePaths[i].substring(relativePaths[i].lastIndexOf("/"),relativePaths[i].length())+"不能删除！,");
					    continue;
                    }
					File file = new File(StaticValue.GROUP_FILE, groupName + relativePaths[i]);
					if (file.isDirectory()) {
						boolean flag = org.nutz.lang.Files.deleteDir(file);
						if (!flag) {
							System.gc();//回收资源
							org.nutz.lang.Files.deleteDir(file.getAbsoluteFile());
						}
						if(file.exists()){
							sb.append("文件夹："+file.getName()+"删除失败！,");
						}else{
							sb.append("文件夹："+file.getName()+"删除成功！,");
						}
					} else {
						boolean flag = org.nutz.lang.Files.deleteFile(file);
						if (!flag) {
							System.gc();//回收资源
							file.delete();
						}
						if(file.exists()){
							sb.append("文件："+file.getName()+"删除失败！,");
						}else{
							sb.append("文件："+file.getName()+"删除成功！,");
						}
					}
				}
				return Restful.instance().ok(true).msg(sb.toString());
			}else{
				List<String> hosts = Arrays.asList(hostPorts);
				Set<String> hostPortsArr = new HashSet<>(hosts);
				Set<String> firstHost = new HashSet<String>();
				if (hostPortsArr.contains(Constants.HOST_MASTER)) {
					hostPortsArr.remove(Constants.HOST_MASTER);
					ArrayList arrayList = new ArrayList(hosts);
					arrayList.remove(Constants.HOST_MASTER);
					firstHost.add(arrayList.get(0).toString());
				}
				String message = proxyService.post(hostPortsArr, "/admin/fileInfo/deleteFile",
						ImmutableMap.of("groupName", groupName, "relativePaths[]", relativePaths, "first", false), 100000,
						ProxyService.MERGE_MESSAGE_CALLBACK);
				//删除master数据节点
				if (firstHost != null && firstHost.size() > 0) {
					List<String> list = new ArrayList<String>();
					for(int a = 0;a<relativePaths.length;a++){
						list.add(relativePaths[a].endsWith("/")?relativePaths[a].substring(0,(relativePaths[a].length() -1)):relativePaths[a]);
					}
					proxyService.post(firstHost, "/admin/fileInfo/upCluster",
							ImmutableMap.of("groupName", groupName, "relativePaths",
									list.toArray(new String[list.size()])), 100000);
				}
				return Restful.instance().ok(true).msg(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Restful.instance().ok(false).msg("删除失败！" + e.getMessage());
		}
	}


	@At
	@AdaptBy(type = UploadAdaptor.class)
	public Restful upload(@Param("groupName") String groupName, @Param("relativePath") String relativePath, @Param("relativePath") TempFile[] fileList) throws IOException {

		if (relativePath.contains("..")) {
			return Restful.instance(false, "上传路径不能包含`..`字符");
		}

		File file = null;

		if (StringUtil.isBlank(relativePath) || "/".equals(relativePath)) {
			file = new File(StaticValue.GROUP_FILE, groupName);
		} else {
			file = new File(StaticValue.GROUP_FILE, groupName + relativePath);
		}


		for (TempFile tempFile : fileList) {
			try {
				File to = new File(file, tempFile.getSubmittedFileName());
				tempFile.write(to.getAbsolutePath());
				LOG.info("write file to " + to.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		return Restful.instance().ok(true).msg("上传成功");
	}

	@At
	@AdaptBy(type = UploadAdaptor.class)
	public Restful uploadFile(@Param("hostPorts") String[] hostPorts, @Param("group_name") String groupName, @Param("filePath") String filePath,
	                          @Param("file") TempFile[] file, @Param("fileNames") String[] fileNames, @Param(value = "first", df = "true") boolean first) throws IOException {
		int fileNum = (int) file.length;

		if (fileNum <= 0) {
			LOG.warn(" not find any file!");
		}

		try {
			if (!first) {
				File folder = null;
				if (StringUtil.isNotBlank(filePath)) {
					folder = new File(StaticValue.GROUP_FILE, groupName + filePath);
					if (!folder.exists()) folder.mkdir();
				}
				for (int i = 0; i < fileNames.length; i++) {
					String fileName = fileNames[i];
					try {
						File to = new File(StaticValue.GROUP_FILE, groupName + filePath + "/" + fileName);
						if (folder != null) {
							String path = folder.getCanonicalPath();
							to = new File(path + "/" + fileName);
						}
						file[i].write(to.getAbsolutePath());
						LOG.info("write file to " + to.getAbsolutePath());
						fileNum++;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				JarService.remove(groupName);
			} else {
				List<String> hosts = Arrays.asList(hostPorts);
				Set<String> hostPortsArr = new HashSet<>(hosts);
				Set<String> firstHost = new HashSet<>();
				if (hosts.contains(Constants.HOST_MASTER)) {
					hostPortsArr.remove(Constants.HOST_MASTER);
					ArrayList arrayList = new ArrayList(hosts);
					arrayList.remove(Constants.HOST_MASTER);
					firstHost.add(arrayList.get(0).toString());
				}
				File[] files = Arrays.stream(file).map(f -> f.getFile()).toArray(File[]::new);
				String[] fns = Arrays.stream(file).map(f -> f.getSubmittedFileName()).toArray(String[]::new);
				proxyService.upload(hostPortsArr, "/admin/fileInfo/uploadFile",
						ImmutableMap.of("group_name", groupName, "file", files, "filePath", filePath,
								"fileNames", fns, "first", false), 100000);
				//同步文件到Master
				String[] relativePaths = Arrays.stream(file).map(f -> (filePath.endsWith("/") ? filePath : filePath + "/") + f.getSubmittedFileName()).toArray(String[]::new);
				proxyService.post(firstHost, "/admin/fileInfo/upCluster",
						ImmutableMap.of("groupName", groupName, "relativePaths",
								relativePaths), 100000);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Restful.instance().ok(false).msg("保存失败！" + e.getMessage());
		}
		return Restful.instance().ok(true).msg("upload " + fileNum + " file ok!");
	}

	/**
	 * 文件复制，如果源无文件则删除目标文件,是拉的方式
	 */
	@At
	public Restful copyFile(@Param("fromHostPort") String fromHostPort, @Param("groupName") String groupName, @Param("relativePaths") String[] relativePaths) throws Exception {
		StringBuilder sb = new StringBuilder();
		boolean flag = true;
		for (String relativePath : relativePaths) {
			if (StringUtil.isBlank(relativePath)) {
				continue;
			}

			long start = System.currentTimeMillis();

			Response post = proxyService.post(fromHostPort, "/admin/fileInfo/downFile", ImmutableMap.of("groupName", groupName, "relativePath", relativePath), 120000);

			File file = new File(StaticValue.GROUP_FILE, groupName + relativePath);

			if (post.getStatus() == 404) { //没找到，那么就删除本地
				org.nutz.lang.Files.deleteFile(file);
				LOG.info("delete file {} -> {} ", groupName, relativePath);
			} else if (post.getStatus() == 200) {
				IOUtil.writeAndClose(post.getStream(), file);
				LOG.info("down ok : {} use time : {} ", relativePath, System.currentTimeMillis() - start);
			} else {
				LOG.error("down error : {} ", post.getContent());
				sb.append(relativePath + " 下载错误: " + post.getContent());
				flag = false;
			}
		}

		return Restful.instance(flag, sb.toString());
	}

	@At
	public Restful upCluster(@Param("groupName") String groupName, @Param("relativePaths") String[] relativePaths) throws Exception {
		for (String relativePath : relativePaths) {
			StaticValue.space().upCluster(groupName, relativePath);
		}
		return Restful.ok();
	}


	/**
	 * 下载开发者工具
	 */
	@At
	@Ok("void")
	public void downSDK(HttpServletResponse response) throws URISyntaxException, IOException {

		File jcoderJarFile = StaticValue.getJcoderJarFile();

		if (jcoderJarFile == null) {
			throw new FileNotFoundException("can not down sdk by idea model");
		}

		response.addHeader("Content-Disposition", "attachment;filename=jcoder_dev.zip");
		response.setContentType("application/octet-stream");

		try (ZipOutputStream out = new ZipOutputStream(response.getOutputStream())) {
			Set<String> sets = new HashSet<>();

			// 写jar包
			String name = "lib/" + jcoderJarFile.getName();
			sets.add(name);
			out.putNextEntry(new ZipEntry(name));
			try (FileInputStream fis = new FileInputStream(jcoderJarFile)) {
				int len;
				byte[] buffer = new byte[10240];
				while ((len = fis.read(buffer)) > 0) {
					out.write(buffer, 0, len);
				}
			}

			String mainCode = "package org.nlpcn.jcoder ;\n" +
					"\n" +
					"\n" +
					"import org.nlpcn.jcoder.util.Testing;\n" +
					"\n" +
					"public class Main {\n" +
					"\tprivate static String host = \"{IP}\";\n" +
					"\tprivate static String zk = \"{ZK}\";\n" +
					"\n" +
					"\tpublic static void main(String[] args) throws Exception {\n" +
					"\t\tTesting.startServer(new String[]{\n" +
					"\t\t\t\t\"--zk=\" + zk,\n" +
					"\t\t\t\t\"--host=\" + host,\n" +
					"\t\t\t\t\"--home=home\"\n" +
					"\t\t});\n" +
					"\t}\n" +
					"}\n";

			mainCode = mainCode.replace("{IP}", StaticValue.getRemoteHost(Mvcs.getReq())).replace("{ZK}", StaticValue.ZK);

			out.putNextEntry(new ZipEntry("src/main/java/org/nlpcn/jcoder/Main.java"));
			out.write((mainCode).getBytes("utf-8"));

			out.putNextEntry(new ZipEntry("src/test/java/package-info.java"));
			out.write(("/**\n" + " * if you need make some jar file write in src package\n" + " */").getBytes());


			String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
					"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
					"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
					"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
					"    <modelVersion>4.0.0</modelVersion>\n" +
					"    <groupId>torg.nlpcn.jcoder</groupId>\n" +
					"    <artifactId>jcoder_dev</artifactId>\n" +
					"    <version>1.0</version>\n" +
					"    <dependencies>\n" +
					"        <dependency>\n" +
					"            <groupId>org.nlpcn.jcoder</groupId>\n" +
					"            <artifactId>jcoder</artifactId>\n" +
					"            <scope>system</scope>\n" +
					"            <systemPath>${basedir}/lib/{JCODER_JAR_NAME}</systemPath>\n" +
					"        </dependency>\n" +
					"    </dependencies>\n" +
					"</project>";
			pom = pom.replace("{JCODER_JAR_NAME}", StaticValue.getJcoderJarFile().getName());
			out.putNextEntry(new ZipEntry("pom.xml"));
			out.write(pom.getBytes("utf-8"));
		}
	}


}
