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
 * GitHub Copilot CLI を利用して実行するエージェントクラスです。<br>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/04/14	Kitagawa	新規作成
 *-->
 */
public class CopilotCliAgent extends AbstractCliAgent {

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public CopilotCliAgent(Context context, AgentConfig agentConfig) {
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
		//env.put("COPILOT_HOME", getContext().getConfig().getApplicationWorkspacePath().resolve(".copilot").normalize().toString());
		return env;
	}

	/**
	 * CLI 実行引数を返します。<br>
	 * @param sessionId セッションID
	 * @return CLI 実行引数
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildStartupCommandArgs(java.lang.String)
	 */
	@Override
	protected List<String> buildStartupCommandArgs(String sessionId) {
		List<String> list = new ArrayList<>();
		if (sessionId != null && !sessionId.isBlank()) {
			list.add("--resume=" + sessionId);
		}
		if (getModel() != null && !getModel().isBlank()) {
			list.add("--model");
			list.add(getModel());
		}
		list.add("--mode");
		list.add("autopilot");
		list.add("--output-format");
		list.add("json");
		list.add("--stream");
		list.add("off");
		list.add("--allow-all-tools");
		list.add("--no-ask-user");
		list.add("--config-dir");
		list.add("./" + AGENT_CONFIG_DIR_COPILOT);
		list.add("--log-dir");
		list.add("./" + AGENT_CONFIG_DIR_COPILOT);
		return list;
	}

	/**
	 * コマンド実行時の準備処理を行います。<br>
	 * @throws Throwable 準備処理で予期せぬエラーが発生した場合にスローされます
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#prepare()
	 */
	@Override
	protected void prepare() throws Throwable {
		Files.createDirectories(getContext().getAgentRootPath().resolve(AGENT_CONFIG_DIR_COPILOT));
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

			if ("session.mcp_server_status_changed".equals(node.path("type").asString())) {
				result = "MCPサーバーのステータスを変更しました。";
			}
			if ("session.mcp_servers_loaded".equals(node.path("type").asString())) {
				result = "MCPサーバーをロードしました。";
			}
			if ("session.skills_loaded".equals(node.path("type").asString())) {
				result = "スキルをロードしました。";
			}
			if ("session.tools_updated".equals(node.path("type").asString())) {
				result = "ツールを更新しました。";
			}
			if ("assistant.turn_start".equals(node.path("type").asString())) {
				result = "エージェント処理中です。";
			}
			if ("tool.execution_start".equals(node.path("type").asString())) {
				String message = "";
				message = node.path("data").path("arguments").path("intent").asString();
				if (message != null && !message.isBlank()) {
					result = "ツール処理実行中です: " + message;
				}
				message = node.path("data").path("arguments").path("summary").asString();
				if (message != null && !message.isBlank()) {
					result = "ツール処理実行中です: " + message;
				}
				if (result == null) {
					result = "ツール処理実行中です。";
				}
			}
			if ("tool.execution_complete".equals(node.path("type").asString())) {
				result = "ツール処理が完了しました。";
			}
			if ("assistant.message".equals(node.path("type").asString())) {
				result = "エージェントがレスポンス中です。";
			}
			if ("assistant.reasoning".equals(node.path("type").asString())) {
				result = "エージェントがレスポンス中です。";
			}
			if ("assistant.turn_end".equals(node.path("type").asString())) {
				result = "エージェント処理が完了しました。";
			}
			if ("subagent.started".equals(node.path("type").asString())) {
				result = "サブエージェント処理が開始されました(" + node.path("data").path("agentDisplayName") + ")。";
			}
			if ("subagent.completed".equals(node.path("type").asString())) {
				result = "サブエージェント処理が完了しました(" + node.path("data").path("agentDisplayName") + ")。";
			}
			if ("session.task_complete".equals(node.path("type").asString())) {
				result = "エージェント処理が完了しました。";
			}
			if ("result".equals(node.path("type").asString())) {
				result = "エージェント処理結果をレスポンス中。";
			}
			if ("user.message".equals(node.path("type").asString())) {
				result = "プロンプトを受付中です。";
			}

			// メッセージフック漏れ確認用エラーコンソール出力
			if (result == null) {
				log.warn("[Internal] CopilotCliAgentのlookupProgressでメッセージフック漏れがあります : " + node.toString());
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
			return null;
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

			// typeがassistant.message以外は解析スキップ
			if (!"assistant.message".equals(node.path("type").asString())) {
				return null;
			}

			// content内容を取得
			StringBuilder contentBuilder = new StringBuilder();
			contentBuilder.append(node.path("data").path("content").asString());
			contentBuilder.append("\n");
			String result = contentBuilder.toString();

			if (result != null && !result.isEmpty()) {
				return result;
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

			String type = node.path("type").asString("");

			// type=assistant.usage以外の場合はスキップ
			if (!"assistant.usage".equals(type)) {
				return null;
			}

			// dataノードが存在しない場合はスキップ
			JsonNode data = node.path("data");
			if (data.isMissingNode() || data.isNull()) {
				return null;
			}

			// ターンごとトークン集計
			long inputTokens = data.path("inputTokens").asLong(0L);
			long outputTokens = data.path("outputTokens").asLong(0L);

			TokenUsage result = new TokenUsage(inputTokens, outputTokens);

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupUsageで予期せぬエラーが発生しました。", e);
			return null;
		}
	}
}
