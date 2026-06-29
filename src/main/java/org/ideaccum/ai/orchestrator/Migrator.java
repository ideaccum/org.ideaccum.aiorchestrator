package org.ideaccum.ai.orchestrator;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * アプリケーションバージョンアップにおける既存リソースのマイグレーション処理を提供します。<br>
 * <p>
 * このクラスでは既に利用中のモジュールの入れ替えを行った際に必要最低限のマイグレーションを提供します。<br>
 * アプリケーションの安定化のタイミングで当該モジュールの削除を検討する。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/06/25	Kitagawa		新規作成
 *-->
 */
public final class Migrator implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(Migrator.class);

	/**
	 * コンストラクタ<br>
	 */
	private Migrator() {
		super();
	}

	/**
	 * 環境設定ファイルおよびリポジトリ配下のpropertiesファイルをyamlに移行します。<br>
	 * @param configFilePath 環境設定ファイルパス(yamlパスとして指定)
	 */
	public static void migrate(String configFilePath) {
		migratePropertiesToYaml(configFilePath);
		migrateOwnerAgentName(configFilePath);
	}

	/**
	 * 環境設定ファイルおよびリポジトリ配下のpropertiesファイルをyamlに移行します。<br>
	 * @param configFilePath 環境設定ファイルパス(yamlパスとして指定)
	 */
	private static void migratePropertiesToYaml(String configFilePath) {
		/*
		 * 環境設定リソースパス
		 */
		Path configYaml = Path.of(configFilePath);
		Path configProp = configYaml.resolveSibling(configYaml.getFileName().toString().replace(".yaml", ".properties"));

		/*
		 * 環境設定リソースマイグレーション
		 */
		migratePropertiesToYaml(configProp, configYaml);

		/*
		 * リポジトリパス配下リソースマイグレーション
		 */
		if (!Files.exists(configYaml)) {
			return;
		}
		try {
			Map<String, String> configMap = YAML.readValue(configYaml.toFile(), new TypeReference<LinkedHashMap<String, String>>() {
			});
			String repoPathname = configMap.get("application.repository.path");
			if (repoPathname != null && !repoPathname.isBlank()) {
				Path repoPath = Path.of(repoPathname);
				if (!Files.isDirectory(repoPath)) {
					return;
				}
				/*
				 * プロジェクト単位マイグレーション
				 */
				try (Stream<Path> projects = Files.list(repoPath)) {
					projects.filter(Files::isDirectory).forEach(projectDir -> {
						Path orchestratorDir = projectDir.resolve(".orchestrator");

						// プロジェクト設定ファイル(project.properties -> project.yaml)
						migratePropertiesToYaml(orchestratorDir.resolve("project.properties"), orchestratorDir.resolve("project.yaml"));

						// エージェント設定ファイル(agents/*.properties -> agents/*.yaml)
						Path agentsDir = orchestratorDir.resolve("agents");
						if (Files.isDirectory(agentsDir)) {
							try (Stream<Path> agentFiles = Files.list(agentsDir)) {
								agentFiles.filter(f -> f.getFileName().toString().endsWith(".properties")).forEach(propsFile -> {
									String baseName = propsFile.getFileName().toString();
									Path yamlFile = agentsDir.resolve(baseName.replace(".properties", ".yaml"));
									migratePropertiesToYaml(propsFile, yamlFile);
								});
							} catch (IOException e) {
								log.warn("エージェントディレクトリの走査に失敗しました。({})", agentsDir, e);
							}
						}
					});
				} catch (IOException e) {
					log.warn("リポジトリディレクトリの走査に失敗しました。({})", repoPath, e);
				}
			}
		} catch (Throwable e) {
			log.warn("環境設定ファイルの読み込みに失敗したため、プロジェクト配下の移行をスキップします。", e);
		}
	}

	/**
	 * リポジトリ配下の全会話ログファイルの agentName "Owner" を "User" に移行します。<br>
	 * @param configFilePath 環境設定ファイルパス
	 */
	private static void migrateOwnerAgentName(String configFilePath) {
		Path configYaml = Path.of(configFilePath);
		if (!Files.exists(configYaml)) {
			return;
		}
		try {
			Map<String, String> configMap = YAML.readValue(configYaml.toFile(), new TypeReference<LinkedHashMap<String, String>>() {
			});
			String repoPathname = configMap.get("application.repository.path");
			if (repoPathname == null || repoPathname.isBlank()) {
				return;
			}
			Path repoPath = Path.of(repoPathname);
			if (!Files.isDirectory(repoPath)) {
				return;
			}
			try (Stream<Path> projects = Files.list(repoPath)) {
				projects.filter(Files::isDirectory).forEach(projectDir -> {
					Path conversationFile = projectDir.resolve(".orchestrator").resolve("conversation.json");
					if (Files.exists(conversationFile)) {
						migrateConversationOwnerAgentName(conversationFile);
					}
				});
			}
		} catch (Throwable e) {
			log.warn("会話ログのエージェント名移行をスキップします。", e);
		}
	}

	/**
	 * 会話ログファイル内の agentName "Owner" を "User" に書き換えます。<br>
	 * @param conversationFile 会話ログファイルパス
	 */
	private static void migrateConversationOwnerAgentName(Path conversationFile) {
		try {
			JsonNode root = JSON.readTree(conversationFile.toFile());
			if (!root.isArray()) {
				return;
			}
			boolean modified = false;
			for (JsonNode node : root) {
				if (node.isObject() && "Owner".equals(node.path("agentName").asString())) {
					((ObjectNode) node).put("agentName", "User");
					modified = true;
				}
			}
			if (modified) {
				JSON.writerWithDefaultPrettyPrinter().writeValue(conversationFile.toFile(), root);
				log.info("会話ログのエージェント名を移行しました。(Owner -> User) : {}", conversationFile.getFileName());
			}
		} catch (Throwable e) {
			log.warn("会話ログのエージェント名移行に失敗しました。({})", conversationFile, e);
		}
	}

	/**
	 * propertiesファイルをyamlファイルに変換します。<br>
	 * yamlファイルが既に存在する場合はスキップします。<br>
	 * @param propFile 変換元propertiesファイルパス
	 * @param yamlFile  変換先yamlファイルパス
	 */
	private static void migratePropertiesToYaml(Path propFile, Path yamlFile) {
		if (!Files.exists(propFile) || Files.exists(yamlFile)) {
			return;
		}
		try {
			Properties properties = new Properties();
			try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(propFile)), StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
			Map<String, String> map = new LinkedHashMap<>();
			properties.stringPropertyNames().stream().sorted().forEach(key -> map.put(key, properties.getProperty(key)));
			YAML.writeValue(yamlFile.toFile(), map);
			Files.delete(propFile);
			log.info("プロパティファイルをYAMLに移行しました。({} -> {})", propFile.getFileName(), yamlFile.getFileName());
		} catch (Throwable e) {
			log.warn("プロパティファイルのYAML移行に失敗しました。({} -> {})", propFile, yamlFile, e);
		}
	}
}
