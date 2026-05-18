package org.ideaccum.ai.orchestrator.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

/**
 * クラスパスリソース操作ユーティリティクラスです。<br>
 * <p>
 * クラスパスリソースの読み込みおよびファイルシステムへのコピーを提供します。<br>
 * JAR実行時とIDE実行時(file://)の両方に対応します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/12	Kitagawa		新規作成
 *-->
 */
public class ResourceUtils {

	/**
	 * コンストラクタ<br>
	 */
	private ResourceUtils() {
	}

	/**
	 * クラスパスリソースをバイト配列として読み込みます。<br>
	 * @param resourcePath クラスパスリソースパス(先頭スラッシュあり)
	 * @return リソースのバイト配列
	 * @throws IOException リソースが見つからない場合または読み込みに失敗した場合にスローされます
	 */
	public static byte[] readResource(String resourcePath) throws IOException {
		try (InputStream is = ResourceUtils.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("リソースが見つかりません(" + resourcePath + ")。");
			}
			return is.readAllBytes();
		}
	}

	/**
	 * クラスパスリソースディレクトリを指定ディレクトリへ再帰的にコピーします。<br>
	 * @param resourceDirPath コピー元クラスパスディレクトリパス(先頭スラッシュあり)
	 * @param destRoot コピー先ディレクトリパス
	 * @throws IOException リソースが見つからない場合またはコピーに失敗した場合にスローされます
	 */
	public static void copyResourceDirectory(String resourceDirPath, Path destRoot) throws IOException {
		URL dirUrl = ResourceUtils.class.getResource(resourceDirPath);
		if (dirUrl == null) {
			throw new IOException("リソースディレクトリが見つかりません(" + resourceDirPath + ")。");
		}
		try {
			URI dirUri = dirUrl.toURI();
			if ("jar".equals(dirUri.getScheme())) {
				try (FileSystem jarFs = FileSystems.newFileSystem(dirUri, Map.of())) {
					copyDirectory(jarFs.getPath(resourceDirPath), destRoot);
				}
			} else {
				copyDirectory(Path.of(dirUri), destRoot);
			}
		} catch (URISyntaxException e) {
			throw new IOException("リソースURIの解析に失敗しました(" + resourceDirPath + ")。", e);
		}
	}

	/**
	 * ディレクトリを再帰的にコピーします。<br>
	 * @param sourceRoot コピー元ルートパス
	 * @param destRoot コピー先ルートパス
	 * @throws IOException コピーに失敗した場合にスローされます
	 */
	private static void copyDirectory(Path sourceRoot, Path destRoot) throws IOException {
		try (Stream<Path> walk = Files.walk(sourceRoot)) {
			for (Path source : (Iterable<Path>) walk::iterator) {
				Path relative = sourceRoot.relativize(source);
				Path dest = destRoot.resolve(relative.toString());
				if (Files.isDirectory(source)) {
					Files.createDirectories(dest);
				} else {
					Files.createDirectories(dest.getParent());
					Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
}
