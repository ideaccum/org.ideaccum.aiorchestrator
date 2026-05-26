package org.ideaccum.ai.orchestrator;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

/**
 * アプリケーション共通の定数を提供するインタフェースクラスです。<br>
 * <p>
 * 処理インタフェースを標準化する目的のクラスではなく、定数を提供する目的で設置されたクラスです。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/08	Kitagawa		新規作成
 *-->
 */
public interface Constants {

	/** JSONオブジェクトマッパー */
	public static final ObjectMapper MAPPER = new ObjectMapper();

	/** 拡張子とContent-Typeのマッピング */
	public static final Map<String, String> MIME_TYPES = Map.of( //
			".html", "text/html; charset=UTF-8", //
			".css", "text/css; charset=UTF-8", //
			".js", "application/javascript; charset=UTF-8", //
			".json", "application/json; charset=UTF-8", //
			".png", "image/png", //
			".ico", "image/x-icon" //
	);

	/** 日付フォーマット */
	public static final DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/** デフォルト環境設定情報プロパティリソース */
	public static final String DEFALUT_CONFIG_FILE = "/default-config.properties";

	/** デフォルトプロジェクト設定リソース */
	public static final String DEFAULT_PROJECT_FILE = "/default-project.properties";

	/** 環境設定情報プロパティリソース */
	public static final String CONFIG_FILE = "config/config.properties";

	/** リソースパス:プロジェクトテンプレート */
	public static final String RESOURCE_TEMPLATE_PROJECT = "/template-project";

	/** リソースパス:エージェントテンプレート(フルスタック) */
	public static final String RESOURCE_TEMPLATE_AGENTS_FULLSTACK = "/template-agents/fullstack";

	/** リソースパス:エージェントテンプレート(通常タスク) */
	public static final String RESOURCE_TEMPLATE_AGENTS_MIDDLE = "/template-agents/middle";

	/** リソースパス:エージェントテンプレート(ライト(Claude)) */
	public static final String RESOURCE_TEMPLATE_AGENTS_LIGHT_CLAUDE = "/template-agents/light-claude";

	/** リソースパス:エージェントテンプレート(ライト(Codex)) */
	public static final String RESOURCE_TEMPLATE_AGENTS_LIGHT_CODEX = "/template-agents/light-codex";

	/** リソースパス:エージェントテンプレート(ライト(Gemini)) */
	public static final String RESOURCE_TEMPLATE_AGENTS_LIGHT_GEMINI = "/template-agents/light-gemini";

	/** オーケストレーター:ホームパス */
	public static final String ORCHESTRATOR_HOME_PATH = ".orchestrator";

	/** オーケストレーター:ログパス */
	public static final String ORCHESTRATOR_LOGS_PATH = ".orchestrator/logs";

	/** オーケストレーター:エージェントパス */
	public static final String ORCHESTRATOR_AGENTS_PATH = ".orchestrator/agents";

	/** オーケストレーター:会話ログパス */
	public static final String ORCHESTRATOR_CONVERSATION_FILE = ".orchestrator/conversation.json";

	/** オーケストレーター:セッションリストパス */
	public static final String ORCHESTRATOR_SESSIONS_FILE = ".orchestrator/sessions.json";

	/** オーナーエージェント名(予約名) */
	public static final String OWNER_AGENT_NAME = "Owner";

	/** エージェント:完了キーワード(全エージェントが合意したと判断したときに発言する文字列) */
	//public static final String AGENT_FINALIZE_KEYWORD ="【目的タスク完了】";
	public static final String AGENT_FINALIZE_KEYWORD = "<<<< Task Completed >>>>";

	/** エージェント:中断キーワード(実行環境問題などでタスク継続が不可能と判断したときに発言する文字列) */
	//public static final String AGENT_INTERRRUPTE_KEYWORD ="【タスク中断】";
	public static final String AGENT_INTERRRUPTE_KEYWORD = "<<<< Task Interrupt >>>>";

	/** エージェント:タスクディスパッチキーワードパターン */
	//public static final String AGENT_DISPATH_REGEX ="【タスクディスパッチ:([^】]+)】";
	public static final String AGENT_DISPATH_REGEX = "<<<< Task Dispatch : '([^']+)' >>>>";

	/** エージェント:タスクディスパッチキーワードパターン */
	//public static final String AGENT_DISPATH_REGEX ="【タスクディスパッチ:([^】]+)】";
	public static final Pattern AGENT_DISPATH_REGEXP = Pattern.compile(AGENT_DISPATH_REGEX);

	/** SSE:接続URL */
	public static final String SSE_CONNECT_URL = "/sse/connect";

	/** SSE:イベントバッファ最大数 */
	public static final int SSE_MAX_BUFFER = 50000;

	/** WebUI:ベースパス */
	public static final String WEBUI_BASE_URL = "/webui";

	/** WebUI:ルートURL */
	public static final String WEBUI_ROOT_URL = "/webui/";

	/** WebUI:リソースベースパス */
	public static final String WEBUI_RESOURCE_BASE = "/webui";

	/** WebUI:テーマCSSパス */
	public static final String WEBUI_THEME_CSS = "/webui/css/theme.css";

	/** WebUI:プロジェクト設定ページURL */
	public static final String WEBUI_PROJECT_SETTING_PAGE = "/webui/project_setting.html";

	/** WebUI:プロセスコントローラーページURL */
	public static final String WEBUI_PROCESS_CONTROLLER_PAGE = "/webui/process_controller.html";

	/** WebUI:エージェント設定ページURL */
	public static final String WEBUI_AGENT_SETTING_PAGE = "/webui/agent_setting.html";

	/** WebUI:環境設定ページURL */
	public static final String WEBUI_CONFIG_SETTING_PAGE = "/webui/config_setting.html";

	/** WebUI:ログビューページURL */
	public static final String WEBUI_LOG_VIEW_PAGE = "/webui/log_view.html";

	/** API:URL(プロジェクトデフォルト値取得) */
	public static final String API_GET_DEFAULT_PROJECT = "/api/get_default_project";

	/** API:URL(プロジェクト選択) */
	public static final String API_SELECT_PROJECT = "/api/select_project";

	/** API:URL(オーケストレーター処理開始) */
	public static final String API_START_ORCHESTRATOR = "/api/start_orchestrator";

	/** API:URL(オーケストレーター処理停止) */
	public static final String API_STOP_ORCHESTRATOR = "/api/stop_orchestrator";

	/** API:URL(オーケストレーターステータス取得) */
	public static final String API_GET_ORCHESTRATOR_STATUS = "/api/get_orchestrator_status";

	/** API:URL(プロジェクト一覧取得) */
	public static final String API_GET_PROJECT_LIST = "/api/get_project_list";

	/** API:URL(カレントプロジェクト取得) */
	public static final String API_GET_CURRENT_PROJECT = "/api/get_current_project";

	/** API:URL(プロジェクト情報取得) */
	public static final String API_GET_PROJECT = "/api/get_project";

	/** API:URL(プロジェクト情報保存) */
	public static final String API_SAVE_PROJECT = "/api/save_project";

	/** API:URL(プロジェクト削除) */
	public static final String API_DELETE_PROJECT = "/api/delete_project";

	/** API:URL(エージェント一覧取得) */
	public static final String API_GET_AGENT_LIST = "/api/get_agent_list";

	/** API:URL(エージェント保存) */
	public static final String API_SAVE_AGENT = "/api/save_agent";

	/** API:URL(エージェント削除) */
	public static final String API_DELETE_AGENT = "/api/delete_agent";

	/** API:URL(環境設定取得) */
	public static final String API_GET_CONFIG = "/api/get_config";

	/** API:URL(環境設定保存) */
	public static final String API_SAVE_CONFIG = "/api/save_config";

	/** API:URL(会話ログ取得) */
	public static final String API_GET_CONVERSATION_LOG = "/api/get_conversation_log";
}
