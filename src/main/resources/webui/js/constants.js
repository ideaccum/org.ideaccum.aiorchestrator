/**
 * アプリケーションWebインタフェース側処理における定数提供クラスです。<br>
 * <p>
 * SSEイベント名や再接続間隔など、Webインタフェース側で使用される定数を一元管理します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/08	Kitagawa		新規作成
 *-->
 */
class Constants {
	/** デフォルトエージェント */
	static DEFAULT_AGENT = "claude-cli";

	/** SSEカスタムイベント名 */
	static SSE_EVENT_NAME = "sse-event";

	/** SSE再接続間隔(ミリ秒) */
	static SSE_RECONNECT_INTERVAL = 3 * 1000; // 3秒

	/** サーバー状態ポーリング間隔(ミリ秒) */
	static POLLING_INTERVAL = 500; // 0.5秒

	/** サーバー状態ポーリング中断タイムアウト(ミリ秒) */
	static POLLING_ABORT_TIMEOUT = 30 * 1000; // 30秒

	/** WebAPI中断タイムアウト(ミリ秒) */
	static WEBAPI_ABORT_TIMEOUT = 120 * 60 * 1000; // 2時間

	/** API処理中オーバーレイ表示ディレイ(ミリ秒) */
	static PROCESSING_OVERLAY_DELAY = 500; // 0.5秒

	/** アイコン: ヒント(電球) */
	static ICON_HINT = '<span class="icon icon-hint"></span>';

	/** アイコン: プロジェクト(レイヤー) */
	static ICON_PROJECT = '<span class="icon icon-project"></span>';

	/** アイコン: エージェント(CPUチップ) */
	static ICON_AGENT = '<span class="icon icon-agent"></span>';

	/** アイコン: 更新(リフレッシュ) */
	static ICON_REFRESH = '<span class="icon icon-refresh"></span>';

	/** アイコン: 追加(新規ファイル) */
	static ICON_PLUS = '<span class="icon icon-plus"></span>';

	/** アイコン: ゴミ箱 */
	static ICON_TRASH = '<span class="icon icon-trash"></span>';

	/** アイコン: 複写 */
	static ICON_COPY = '<span class="icon icon-copy"></span>';

	/** オーナーエージェント名(予約名) */
	static OWNER_AGENT_NAME = "Owner";

	/** プリセットプロンプト:エージェント存在確認 */
	static PROMPT_CHECK_AGENTS = "各エージェントの稼働状況を確認してください。\n- トークンの不要な消費をさけるため、稼働状況確認以外のタスクは実行しないでください。\n- エージェント稼働確認はエージェント自身に必ず発言させてください。";

	/** プリセットプロンプト:過去会話のサマリー */
	static PROMPT_SUMMARY = "これまでの会話ログを振り返り、実施した作業の概要・現在の進捗状況・残タスクをまとめて報告してください。\n- リーダーのエージェントがこのタスクを実行してください。";

	/** プリセットプロンプト:推奨される次の指示 */
	static PROMPT_NEXT_ACTION = "現在の進捗状況を踏まえ、次に実施すべき推奨アクションを具体的に提案してください。\n- リーダーのエージェントがこのタスクを実行してください。";

	/** プリセットプロンプト:自動運転議論 */
	static PROMPT_NEXT_DISCUSSION_AUTO_PILOT = "AI搭載の自動運転について討論してください。\n自身の進行方向には子どもが飛び出してきています、それを避けようとすると対向車と正面衝突します。\n回避せずに子どもを犠牲にする行動、回避して自身と対向車の運転手が犠牲になる行動のパターンがありますが、どの行動が妥当ですか。\n尚、ブレーキをかけても間に合う状況にはなく、誰かが犠牲になります。";

	/** SSE接続URL */
	static SSE_CONNECT_URL = "/sse/connect";

	/** SSEイベント(接続済み) */
	static SSE_TYPE_CONNECTED = "connected";

	/** SSEイベント(切断済み) */
	static SSE_TYPE_DISCONNECTED = "disconnected";

	/** SSEイベント(エージェント初期化済み) */
	static SSE_TYPE_AGENT_INITIALIZED = "agent_initialized";

	/** SSEイベント(エージェント開始) */
	static SSE_TYPE_AGENT_START = "agent_start";

	/** SSEイベント(エージェントレスポンス) */
	static SSE_TYPE_AGENT_CONTENT = "agent_content";

	/** SSEイベント(エージェント一般メッセージ) */
	static SSE_TYPE_AGENT_MESSAGE = "agent_message";

	/** SSEイベント(エージェント処理終了) */
	static SSE_TYPE_AGENT_FINISH = "agent_finish";

	/** SSEイベント(エージェントエラー) */
	static SSE_TYPE_AGENT_ERROR = "agent_error";

	/** SSEイベント(エージェントセッションID更新) */
	static SSE_TYPE_AGENT_SESSION_UPDATED = "agent_session_updated";

	/** SSEイベント(オーケストレーター完了) */
	static SSE_TYPE_ORCHESTRATOR_DONE = "orchestrator_done";

	/** SSEイベント(オーケストレーター開始) */
	static SSE_TYPE_ORCHESTRATOR_STARTED = "orchestrator_started";

	/** SSEイベント(オーケストレーター停止) */
	static SSE_TYPE_ORCHESTRATOR_STOPPED = "orchestrator_stopped";

	/** API URL(プロジェクト選択) */
	static API_SELECT_PROJECT = "/api/select_project";

	/** API URL(プロジェクトデフォルト値取得) */
	static API_GET_DEFAULT_PROJECT = "/api/get_default_project";

	/** API URL(オーケストレーター処理開始) */
	static API_START_ORCHESTRATOR = "/api/start_orchestrator";

	/** API URL(オーケストレーター処理停止) */
	static API_STOP_ORCHESTRATOR = "/api/stop_orchestrator";

	/** API URL(オーケストレーターステータス取得) */
	static API_GET_ORCHESTRATOR_STATUS = "/api/get_orchestrator_status";

	/** API URL(プロジェクト一覧取得) */
	static API_GET_PROJECT_LIST = "/api/get_project_list";

	/** API URL(カレントプロジェクト取得) */
	static API_GET_CURRENT_PROJECT = "/api/get_current_project";

	/** API URL(プロジェクト情報取得) */
	static API_GET_PROJECT = "/api/get_project";

	/** API URL(プロジェクト情報保存) */
	static API_SAVE_PROJECT = "/api/save_project";

	/** API URL(プロジェクト削除) */
	static API_DELETE_PROJECT = "/api/delete_project";

	/** API URL(エージェント一覧取得) */
	static API_GET_AGENT_LIST = "/api/get_agent_list";

	/** API URL(エージェント保存) */
	static API_SAVE_AGENT = "/api/save_agent";

	/** API URL(エージェント削除) */
	static API_DELETE_AGENT = "/api/delete_agent";

	/** API URL(環境設定取得) */
	static API_GET_CONFIG = "/api/get_config";

	/** API URL(環境設定保存) */
	static API_SAVE_CONFIG = "/api/save_config";

	/** API URL(会話ログ取得) */
	static API_GET_CONVERSATION_LOG = "/api/get_conversation_log";

	/** API URL(セッションクリア) */
	static API_CLEAR_SESSION = "/api/clear_session";

	/** API URL(制御キーワード取得) */
	static API_GET_CONTROL_KEYWORDS = "/api/get_control_keywords";

	/** API URL(ログダウンロード) */
	static API_DOWNLOAD_LOGS = "/api/download_logs";

	/** API URL(バックグラウンドタスクキャンセル) */
	static API_CANCEL_PROCESS = "/api/cancel_process";

	/** API URL(バックグラウンドタスクステータス取得) */
	static API_GET_PROCESS_STATUS = "/api/get_process_status";
}
