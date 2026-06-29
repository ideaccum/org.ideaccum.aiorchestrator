package org.ideaccum.ai.orchestrator.agents;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ideaccum.ai.orchestrator.agent.AgentConfig;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.TokenUsage;
import org.ideaccum.ai.orchestrator.util.StringUtils;

import tools.jackson.databind.JsonNode;

/**
 * Codex CLI を子プロセスとして実行するエージェントクラスです。<br>
 * <p>
 * Codex CLI 固有の起動引数・出力解析設定を定数として保持します。<br>
 * エージェントクラスインスタンスはセッションごとに単一のインスタンスを想定したクラス設計になっています。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/03/31	Kitagawa	新規作成
 * 2026/05/26	Kitagawa	トークン集計不具合を修正
 *-->
 */
public class CodexCliAgent extends AbstractCliAgent {

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public CodexCliAgent(Context context, AgentConfig agentConfig) {
		super(context, agentConfig);
	}

	/**
	 * コマンド実行時の環境変数マップを生成します。<br>
	 * @return コマンド実行時の環境変数マップ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildEnvironmentMap()
	 */
	@Override
	protected Map<String, String> buildEnvironmentMap() {
		Map<String, String> env = new HashMap<>();
		//env.put("CODEX_HOME", getContext().getConfig().getApplicationWorkspacePath().resolve(".codex").toAbsolutePath().toString());
		return env;
	}

	/**
	 * CLI起動時引数を返します。<br>
	 * @param sessionId セッションID(セッション継続起動時は非null、初回起動時はnull)
	 * @return CLI起動時引数
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildStartupCommandArgs(java.lang.String)
	 */
	@Override
	protected List<String> buildStartupCommandArgs(String sessionId) {
		List<String> list = new ArrayList<>();
		list.add("exec");
		if (sessionId != null && !sessionId.isBlank()) {
			list.add("resume");
			list.add(sessionId);
		} else {
			// CodexはセッションID指定による新規開始強制をサポートしない
		}
		//list.add("--full-auto");
		list.add("-c");
		list.add("'sandbox_mode=\"workspace-write\"'");
		list.add("-c");
		list.add("'approval_polict=\"never\"'");
		if (getModel() != null && !getModel().isBlank()) {
			list.add("--model");
			list.add(getModel());
		}
		//list.add("--profile");
		//list.add(".codex/config.toml");
		list.add("--skip-git-repo-check");
		list.add("--json");
		//list.add("--ephemeral"); // セッションロールアウト禁止
		list.add("--config");
		list.add("features.agent_tools=false"); // サブエージェント動作禁止
		list.add("--config");
		list.add("agents.max_depth=1"); // サブエージェント動作禁止(追加設定)
		list.add("-"); // 標準入力からプロンプトを受け取る指定
		return list;
	}

	/**
	 * コマンド実行時の準備処理を行います。<br>
	 * @throws Throwable 準備処理で予期せぬエラーが発生した場合にスローされます
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#prepare()
	 */
	@Override
	protected void prepare() throws Throwable {
		Files.createDirectories(getContext().getAgentRootPath().resolve(AGENT_CONFIG_DIR_CODEX));
	}

	/**
	 * エージェントからのエラーレスポンスを抽出します。<br>
	 * このレスポンスがあった場合は後続処理は中断されます。<br>
	 * @param response エージェントレスポンス
	 * @return エラーメッセージ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupError(java.lang.String)
	 */
	protected String lookupError(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		String result = null;
		try {
			if (StringUtils.isJSON(response)) {
				return result;
			}

			if (response.indexOf("HTTP error: 401 Unauthorized") >= 0) {
				result = "認証が行われていません。";
			}

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupErrorで予期せぬエラーが発生しました。", e);
			return response;
		}
	}

	/**
	 * エージェントからの実行進捗レスポンスを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return 実行進捗メッセージ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupProgress(java.lang.String)
	 */
	protected String lookupProgress(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		String result = null;
		try {
			if (!StringUtils.isJSON(response)) {
				return result;
			}

			JsonNode node = JSON.readTree(response);
			String type = node.path("type").asString();

			if ("thread.started".equals(type)) {
				result = "スレッドを開始しました。";
			} else if ("turn.started".equals(type)) {
				result = "エージェント処理を開始しました。";
			} else if ("item.started".equals(type)) {
				JsonNode item = node.path("item");
				String itemType = item.path("type").asString();
				if ("command_execution".equals(itemType)) {
					String command = item.path("command").asString();
					result = "コマンド実行　: " + command;
				} else if ("web_search".equals(itemType)) {
					String query = item.path("query").asString();
					result = "Web検索　: " + query;
				} else if ("file_change".equals(itemType)) {
					String query = item.path("query").asString();
					result = "ファイル変更　: " + query;
				} else if ("todo_list".equals(itemType)) {
					int total = item.path("items").size();
					result = "タスクリスト開始　: " + total + "件";
				}
			} else if ("item.updated".equals(type)) {
				JsonNode item = node.path("item");
				String itemType = item.path("type").asString();
				if ("todo_list".equals(itemType)) {
					JsonNode items = item.path("items");
					long completed = 0;
					for (JsonNode it : items) {
						if (it.path("completed").asBoolean(false)) {
							completed++;
						}
					}
					result = "タスクリスト更新　: " + completed + "/" + items.size() + "件完了。";
				}
			} else if ("item.completed".equals(type)) {
				JsonNode item = node.path("item");
				String itemType = item.path("type").asString();
				if ("agent_message".equals(itemType)) {
					result = item.path("text").asString();
				} else if ("command_execution".equals(itemType)) {
					result = "コマンド実行が完了しました。";
				} else if ("web_search".equals(itemType)) {
					result = "Web検索が完了しました。";
				} else if ("file_change".equals(itemType)) {
					result = "ファイルが変更されました。";
				} else if ("todo_list".equals(itemType)) {
					result = "タスクリストが完了しました。";
				}
			} else if ("turn.completed".equals(type)) {
				result = "エージェント処理が完了しました。";
			} else if ("turn.failed".equals(type) || "error".equals(type)) {
				result = "エラー発生が発生しました。\n" + node.asString();
			}

			// メッセージフック漏れ確認用エラーコンソール出力
			if (result == null) {
				log.warn("[Internal] CodexCliAgentのlookupProgressでメッセージフック漏れがあります : " + node.toString());
				result = node.toString();
			}

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupProgressで予期せぬエラーが発生しました。", e);
			return response;
		}
	}

	/**
	 * エージェントレスポンスからセッションIDを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return セッションID(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupSessionId(java.lang.String)
	 */
	@Override
	protected String lookupSessionId(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JSON.readTree(response);

			// content内容を取得
			String result = node.path("thread_id").asString();

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupSessionIdで予期せぬエラーが発生しました。", e);
			return null;
		}
	}

	/**
	 * エージェントレスポンスからコンテンツ内容を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return コンテンツ内容(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupContent(java.lang.String)
	 */
	@Override
	protected String lookupContent(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JSON.readTree(response);

			// item.typeがagent_message以外は解析スキップ
			if (!"agent_message".equals(node.path("item").path("type").asString())) {
				return null;
			}

			// content内容を取得
			String result = node.path("item").path("text").asString();

			if (result != null && !result.isEmpty()) {
				return result = result + "\n";
			} else {
				return null;
			}
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupContentで予期せぬエラーが発生しました。", e);
			return null;
		}
	}

	/**
	 * エージェントレスポンスからトークン使用量を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return トークン使用量(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupUsage(java.lang.String)
	 */
	@Override
	protected TokenUsage lookupUsage(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JSON.readTree(response);

			// typeがturn.completed以外は解析スキップ
			if (!"turn.completed".equals(node.path("type").asString())) {
				return null;
			}

			// usageが存在しない場合はスキップ
			JsonNode usageNode = node.path("usage");
			if (usageNode.isMissingNode() || usageNode.isNull()) {
				return null;
			}

			// トークン情報取得
			long inputTokens = node.path("usage").path("input_tokens").asLong(0L);
			long outputTokens = node.path("usage").path("output_tokens").asLong(0L);

			TokenUsage result = new TokenUsage(inputTokens, outputTokens);

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupUsageで予期せぬエラーが発生しました。", e);
			return null;
		}
	}
}
