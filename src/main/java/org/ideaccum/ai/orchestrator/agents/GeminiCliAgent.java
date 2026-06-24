package org.ideaccum.ai.orchestrator.agents;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ideaccum.ai.orchestrator.agent.AgentConfig;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.TokenUsage;
import org.ideaccum.ai.orchestrator.util.StringUtils;

import tools.jackson.databind.JsonNode;

/**
 * Gemini CLI を子プロセスとして実行するエージェントクラスです。<br>
 * <p>
 * Gemini CLI 固有の起動引数・出力解析設定を定数として保持します。<br>
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
public class GeminiCliAgent extends AbstractCliAgent {

	/** チャンク継続状態フラグ */
	private boolean deltaStatus;

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public GeminiCliAgent(Context context, AgentConfig agentConfig) {
		super(context, agentConfig);
		this.deltaStatus = false;
	}

	/**
	 * コマンド実行時の環境変数マップを生成します。<br>
	 * @return コマンド実行時の環境変数マップ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildEnvironmentMap()
	 */
	@Override
	protected Map<String, String> buildEnvironmentMap() {
		Map<String, String> env = new HashMap<>();
		//env.put("GEMINI_CLI_HOME", "./");
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
		// エージェントモデル指定
		if (getModel() != null && !getModel().isBlank()) {
			list.add("--model");
			list.add(getModel());
		}

		// プリントモード時の出力形式(text、json、stream-json)
		list.add("--output-format");
		list.add("stream-json");

		// セッション再開
		if (sessionId != null && !sessionId.isBlank()) {
			list.add("--resume");
			list.add(sessionId);
		}

		// フォルダ信頼確認スキップ
		list.add("--skip-trust");

		// 新規セッション指定
		// Gemini CLI はセッションID指定による新規開始強制をサポートしない

		return list;
	}

	/**
	 * コマンド実行時の準備処理を行います。<br>
	 * @throws Throwable 準備処理で予期せぬエラーが発生した場合にスローされます
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#prepare()
	 */
	@Override
	protected void prepare() throws Throwable {
		Files.createDirectories(getContext().getAgentRootPath().resolve(AGENT_CONFIG_DIR_GEMINI));
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

			if (response.startsWith("Error authenticating")) {
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
			if (response.startsWith("Loaded cached credentials")) {
				result = "認証キャッシュをロードしました。";
			}
			if (response.startsWith("Ripgrep is not available")) {
				result = "Ripgrepツールが存在しません(インストールを推奨します)。";
			}
			if (response.indexOf("You have exhausted your capacity on this model") >= 0) {
				Matcher matcher = Pattern.compile(".*Retrying after ([0-9]+ms).*").matcher(response);
				if (matcher.find()) {
					result = "リクエストキャパシティ超過のため、処理を待機します(" + matcher.group(1) + ")。";
				} else {
					result = "リクエストキャパシティ超過のため、処理を待機します。";
				}
			}

			if (!StringUtils.isJSON(response)) {
				return result;
			}

			JsonNode node = MAPPER.readTree(response);
			String type = node.path("type").asString();

			if ("init".equals(type)) {
				result = "エージェントモデルの準備中です。";
			}
			if ("message".equals(type)) {
				if ("user".equals(node.path("role").asString())) {
					result = "プロンプトを受け付けしました。";
				}
				if ("assistant".equals(node.path("role").asString())) {
					result = "プロンプト実行結果をレスポンス中です。";
				}
			}
			if ("tool_use".equals(type)) {
				if ("update_topic".equals(node.path("tool_name").asString())) {
					result = "トピック更新 : " + node.path("parameters").path("title").asString();
				}
				if ("enter_plan_mode".equals(node.path("tool_name").asString())) {
					result = "プランモードでの実行に切り替えます。";
				}
				if ("exit_plan_mode".equals(node.path("tool_name").asString())) {
					result = "プランモードでの実行を終了します。";
				}
				if ("grep_serach".equals(node.path("tool_name").asString())) {
					result = "Grep検索 : " + node.path("parameters").path("pattern").asString();
				}
				if ("read_file".equals(node.path("tool_name").asString())) {
					result = "ファイル読み込み : " + node.path("parameters").path("file_path").asString();
				}
				if ("wrtite_file".equals(node.path("tool_name").asString())) {
					result = "ファイル書き込み : " + node.path("parameters").path("file_path").asString();
				}
				if ("replace".equals(node.path("tool_name").asString())) {
					result = "ファイル更新 : " + node.path("parameters").path("file_path").asString();
				}
				if ("list_directory".equals(node.path("tool_name").asString())) {
					result = "ディレクトリ取得 : " + node.path("parameters").path("dir_path").asString();
				}
				if ("google_web_search".equals(node.path("tool_name").asString())) {
					result = "Web検索 : " + node.path("parameters").path("query").asString();
				}
				if ("invoke_agent".equals(node.path("tool_name").asString())) {
					result = "エージェント呼び出し: " + node.path("parameters").path("agent_name").asString();
				}
				if ("run_shell_command".equals(node.path("tool_name").asString()) || "shell".equals(node.path("tool_name").asString())) {
					result = "シェルコマンド実行: " + node.path("parameters").path("command").asString();
				}
			}
			if ("tool_result".equals(type)) {
				result = "ツール結果 : " + node.path("output").asString();
			}
			if ("error".equals(type)) {
				result = "エラー : " + node.path("message").asString();
			}
			if ("result".equals(type)) {
				result = "エージェント処理が完了しました。";
			}

			// メッセージフック漏れ確認用エラーコンソール出力
			if (result == null) {
				log.warn("[Internal] GeminiCliAgentのlookupProgressでメッセージフック漏れがあります : " + node.toString());
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
			JsonNode node = MAPPER.readTree(response);

			// content内容を取得
			String result = node.path("session_id").asString().trim();

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
			JsonNode node = MAPPER.readTree(response);

			// typeがmessage以外は解析スキップ
			if (!"message".equals(node.path("type").asString())) {
				if (deltaStatus) {
					deltaStatus = false;
					return "\n";
				}
				return null;
			}

			// roleがassistant以外は解析スキップ
			if (!"assistant".equals(node.path("role").asString())) {
				if (deltaStatus) {
					deltaStatus = false;
					return "\n";
				}
				return null;
			}

			// content内容を取得
			String result = node.path("content").asString();

			if (result != null && !result.isEmpty()) {
				// deltaがfalseの場合は改行追加
				if (!node.path("delta").asBoolean()) {
					deltaStatus = false;
					return result + "\n";
				} else {
					deltaStatus = true;
					return result;
				}
			} else {
				if (deltaStatus) {
					deltaStatus = false;
					return "\n";
				}
				return null;
			}
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupContentで予期せぬエラーが発生しました。", e);
			if (deltaStatus) {
				deltaStatus = false;
				return "\n";
			}
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
			JsonNode node = MAPPER.readTree(response);

			// エラーのみレスポンスはスキップ("error" フィールドが存在し、"response"が空の場合)
			JsonNode errorNode = node.path("error");
			if (!errorNode.isMissingNode() && !errorNode.isNull()) {
				JsonNode responseNode = node.path("response");
				if (responseNode.isMissingNode() || responseNode.isNull() || responseNode.asString("").isEmpty()) {
					return null;
				}
			}

			// statsが存在しない場合はスキップ
			JsonNode statsNode = node.path("stats");
			if (statsNode.isMissingNode() || statsNode.isNull()) {
				return null;
			}

			// stats.input_tokens、stats.output_tokens が直接存在する場合
			long newFormatInput = statsNode.path("input_tokens").asLong(-1L);
			long newFormatOutput = statsNode.path("output_tokens").asLong(-1L);
			if (newFormatInput >= 0 && newFormatOutput >= 0) {
				return new TokenUsage(newFormatInput, newFormatOutput);
			}

			// stats.models[*].tokens.prompt、candidates、thoughtsの場合は後続で加算
			JsonNode modelsNode = statsNode.path("models");
			if (modelsNode.isMissingNode() || modelsNode.isNull()) {
				return null;
			}

			// カテゴリごとトークン合算
			long promptTokens = 0L; // 入力
			long candidateTokens = 0L; // 出力
			long thoughtsTokens = 0L; // 思考(課金対象・出力扱い)
			for (JsonNode modelNode : modelsNode) {
				JsonNode tokens = modelNode.path("tokens");
				if (tokens.isMissingNode() || tokens.isNull()) {
					continue;
				}
				promptTokens += tokens.path("prompt").asLong(0L);
				candidateTokens += tokens.path("candidates").asLong(0L);
				thoughtsTokens += tokens.path("thoughts").asLong(0L);
				// cachedはpromptのサブセットなので加算不要
				// toolはtotalに含まれるので加算不要
			}

			long inputTokens = promptTokens;
			long outputTokens = candidateTokens + thoughtsTokens;

			TokenUsage result = new TokenUsage(inputTokens, outputTokens);

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupUsageで予期せぬエラーが発生しました。", e);
			return null;
		}
	}
}
