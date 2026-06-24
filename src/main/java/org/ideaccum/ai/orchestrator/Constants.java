package org.ideaccum.ai.orchestrator;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
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
			".ico", "image/x-icon", //
			".svg", "image/svg+xml" //
	);

	/** バリデーション:半角英数字チェック正規表現 */
	public static final String ALPHANUMERIC_REGEX = "[a-zA-Z0-9]+";

	/** バリデーション:行区切り文字正規表現(CR/LF/CRLF対応) */
	public static final String LINE_SEPARATOR_REGEX = "[\\r\\n]+";

	/** HTTP:OKステータスコード */
	public static final int HTTP_STATUS_OK = 200;

	/** HTTP:リダイレクトステータスコード */
	public static final int HTTP_STATUS_REDIRECT = 302;

	/** HTTP:フォービッデンステータスコード */
	public static final int HTTP_STATUS_FORBIDDEN = 403;

	/** 日付フォーマット */
	public static final DateFormat DATE_FORMAT_YYYY_MM_DD_HH_MM_SS = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/** 日付フォーマット */
	public static final DateFormat DATE_FORMAT_YYYYMMDD_HHMMSS = new SimpleDateFormat("yyyyMMdd-HHmmss");

	/** 日付フォーマット */
	public static final DateTimeFormatter DATE_FORMATETTR_YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/** 日付フォーマット */
	public static final DateTimeFormatter DATE_FORMATTER_YYYYMMDD_HHMMSS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

	/** リソースパス:エージェントテンプレート(スタンダード) */
	public static final String RESOURCE_TEMPLATE_AGENTS_MIDDLE = "/template-agents/middle";

	/** リソースパス:エージェントテンプレート(ライトウェイト) */
	public static final String RESOURCE_TEMPLATE_AGENTS_LIGHT = "/template-agents/light";

	/** リソースパス:エージェントテンプレート(プログラミング) */
	public static final String RESOURCE_TEMPLATE_AGENTS_PROGRAMMING = "/template-agents/programming";

	/** リソースパス:エージェントテンプレート(討論) */
	public static final String RESOURCE_TEMPLATE_AGENTS_DISCUSSION = "/template-agents/discussion";

	/** プロンプトテンプレート:開始プロンプト */
	public static final String RESOURCE_TEMPLATE_START_PROMPT = "/prompt/start-prompt.vm";

	/** プロンプトテンプレート:リーダープロンプト */
	public static final String RESOURCE_TEMPLATE_LEADER_PROMPT = "/prompt/leader-prompt.vm";

	/** プロンプトテンプレート:継続プロンプト */
	public static final String RESOURCE_TEMPLATE_CONTINUE_PROMPT = "/prompt/continue-prompt.vm";

	/** プロンプトテンプレート:リトライプロンプト */
	public static final String RESOURCE_TEMPLATE_RETRY_PROMPT = "/prompt/retry-prompt.vm";

	/** オーケストレーター:ホームパス */
	public static final String ORCHESTRATOR_HOME_PATH = ".orchestrator";

	/** オーケストレーター:ログパス */
	public static final String ORCHESTRATOR_LOGS_PATH = ".orchestrator/logs";

	/** オーケストレーター:メモリパス */
	public static final String ORCHESTRATOR_MEMORY_PATH = ".orchestrator/memory";

	/** オーケストレーター:エージェントパス */
	public static final String ORCHESTRATOR_AGENTS_PATH = ".orchestrator/agents";

	/** オーケストレーター:会話ログパス */
	public static final String ORCHESTRATOR_CONVERSATION_FILE = ".orchestrator/conversation.json";

	/** オーケストレーター:セッションリストパス */
	public static final String ORCHESTRATOR_SESSIONS_FILE = ".orchestrator/sessions.json";

	/** オーナーエージェント名(予約名) */
	public static final String OWNER_AGENT_NAME = "Owner";

	/** エージェント:デフォルトタイプ */
	public static final String AGENT_DEFAULT_TYPE = "claude-cli";

	/** 処理停止監視スレッドポーリング間隔(ミリ秒) */
	public static final int PROCESS_STOP_MONITOR_INTERVAL = 100;

	/** 処理サービスエグゼキューター終了待機タイムアウト(秒) */
	public static final int PROCESS_AWAIT_TERMINATION_SECONDS = 10;

	/** エージェント設定ディレクトリ:Claude */
	public static final String AGENT_CONFIG_DIR_CLAUDE = ".claude";

	/** エージェント設定ディレクトリ:Gemini */
	public static final String AGENT_CONFIG_DIR_GEMINI = ".gemini";

	/** エージェント設定ディレクトリ:Antigravity */
	public static final String AGENT_CONFIG_DIR_ANTIGRAVITY = ".gemini";

	/** エージェント設定ディレクトリ:Codex */
	public static final String AGENT_CONFIG_DIR_CODEX = ".codex";

	/** エージェント設定ディレクトリ:Copilot */
	public static final String AGENT_CONFIG_DIR_COPILOT = ".copilot";

	/** エージェント:完了キーワード(全エージェントが合意したと判断したときに発言する文字列) */
	//public static final String AGENT_FINALIZE_KEYWORD ="【目的タスク完了】";
	public static final String AGENT_FINALIZE_KEYWORD = "<<<< Task Completed >>>>";

	/** エージェント:中断キーワード(実行環境問題などでタスク継続が不可能と判断したときに発言する文字列) */
	//public static final String AGENT_INTERRRUPTE_KEYWORD ="【タスク中断】";
	public static final String AGENT_INTERRRUPTE_KEYWORD = "<<<< Task Interrupt >>>>";

	/** エージェント:タスクディスパッチキーワードパターン */
	//public static final String AGENT_DISPATH_REGEX ="【タスクディスパッチ:([^】]+)】";
	public static final String AGENT_DISPATH_REGEX = "<<<< Task Dispatch : '?([^'<>]+?)'? >>>>";

	/** エージェント:タスクディスパッチキーワードパターン */
	//public static final String AGENT_DISPATH_REGEX ="【タスクディスパッチ:([^】]+)】";
	public static final Pattern AGENT_DISPATH_REGEXP = Pattern.compile(AGENT_DISPATH_REGEX);

	/** プロンプト:会話ログが存在しない場合のフォールバックメッセージ */
	public static final String AGENT_PROMPT_HISTORY_NO_LOG = "**会話ログは存在しません**";

	/** プロンプト:セッションあり・未読履歴なし時のフォールバックメッセージ(CLIセッションにコンテキスト保持済み) */
	public static final String AGENT_PROMPT_HISTORY_SESSION_CONTINUE = "**あなたは直前のターンで既に発言した状態であるため、セッションの会話コンテキストを踏まえて引き続き対応してください**";

	/** キーワードなし時のリトライ回数上限 */
	public static final int AGENT_RETRY_COUNT = 3;

	/** バリデーション:プロジェクト名最大文字数 */
	public static final int MAX_PROJECT_NAME_LENGTH = 32;

	/** バリデーション:プロジェクトタイトル最大文字数 */
	public static final int MAX_PROJECT_TITLE_LENGTH = 80;

	/** バリデーション:エージェント名最大文字数 */
	public static final int MAX_AGENT_NAME_LENGTH = 32;

	/** バリデーション:モデル名最大文字数 */
	public static final int MAX_MODEL_NAME_LENGTH = 32;

	/** バリデーション:追加引数最大文字数 */
	public static final int MAX_EXTRA_ARGS_LENGTH = 128;

	/** バリデーション:エージェント役割名最大文字数 */
	public static final int MAX_AGENT_ROLE_LENGTH = 80;

	/** バリデーション:ポート番号最大値 */
	public static final int MAX_PORT_NUMBER = 65535;

	/** SSE:接続URL */
	public static final String SSE_CONNECT_URL = "/sse/connect";

	/** SSE:イベントバッファ最大数 */
	public static final int SSE_MAX_BUFFER = 50000;

	/** SSEイベントタイプ:エージェント初期化完了 */
	public static final String EVENT_AGENT_INITIALIZED = "agent_initialized";

	/** SSEイベントタイプ:エージェント処理開始 */
	public static final String EVENT_AGENT_START = "agent_start";

	/** SSEイベントタイプ:エージェントコンテンツ */
	public static final String EVENT_AGENT_CONTENT = "agent_content";

	/** SSEイベントタイプ:エージェント一般メッセージ */
	public static final String EVENT_AGENT_MESSAGE = "agent_message";

	/** SSEイベントタイプ:エージェント処理完了 */
	public static final String EVENT_AGENT_FINISH = "agent_finish";

	/** SSEイベントタイプ:エージェントエラー */
	public static final String EVENT_AGENT_ERROR = "agent_error";

	/** SSEイベントタイプ:エージェントセッションID更新 */
	public static final String EVENT_AGENT_SESSION_UPDATED = "agent_session_updated";

	/** SSEイベントタイプ:オーケストレーター処理開始 */
	public static final String EVENT_ORCHESTRATOR_STARTED = "orchestrator_started";

	/** SSEイベントタイプ:オーケストレーター処理完了 */
	public static final String EVENT_ORCHESTRATOR_DONE = "orchestrator_done";

	/** SSEイベントタイプ:オーケストレーター停止 */
	public static final String EVENT_ORCHESTRATOR_STOPPED = "orchestrator_stopped";

	/** SSEイベントタイプ:プロジェクト変更 */
	public static final String EVENT_PROJECT_CHANGED = "project_changed";

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

	/** API:URL(セッションクリア) */
	public static final String API_CLEAR_SESSION = "/api/clear_session";

	/** API:URL(制御キーワード取得) */
	public static final String API_GET_CONTROL_KEYWORDS = "/api/get_control_keywords";

	/** API:URL(ログダウンロード) */
	public static final String API_DOWNLOAD_LOGS = "/api/download_logs";

	/** API:URL(バックグラウンドプロセスキャンセル) */
	public static final String API_CANCEL_PROCESS = "/api/cancel_process";

	/** API:URL(バックグラウンドプロセスステータス取得) */
	public static final String API_GET_PROCESS_STATUS = "/api/get_process_status";
}
