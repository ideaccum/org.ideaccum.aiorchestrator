/**
 * アプリケーションWebインタフェース側処理における定数提供クラスです。<br>
 * <p>
 * SSEイベント名や再接続間隔など、Webインタフェース側で使用される定数を一元管理します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
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
	static POLLING_INTERVAL = 2 * 1000; // 2秒

	/** サーバー状態ポーリング中断タイムアウト(ミリ秒) */
	static POLLING_ABORT_TIMEOUT = 30 * 1000; // 30秒

	/** WebAPI中断タイムアウト(ミリ秒) */
	static WEBAPI_ABORT_TIMEOUT = 60 * 60 * 1000; // 1時間

	/** アイコン: ヒント(電球) */
	static ICON_HINT = `<svg xmlns="http://www.w3.org/2000/svg" width="1.0em" height="1.0em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M12 2a7 7 0 0 1 7 7c0 2.5-1.3 4.7-3.3 6l-.7 1H9l-.7-1A7 7 0 0 1 12 2z"/></svg>`;

	/** アイコン: プロジェクト(レイヤー) */
	static ICON_PROJECT = `<svg xmlns="http://www.w3.org/2000/svg" width="1.1em" height="1.1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>`;

	/** アイコン: エージェント(CPUチップ) */
	static ICON_AGENT = `<svg xmlns="http://www.w3.org/2000/svg" width="1.1em" height="1.1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>`;

	/** アイコン: 更新(リフレッシュ) */
	static ICON_REFRESH = `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>`;

	/** アイコン: 追加(新規ファイル) */
	static ICON_PLUS = `<svg xmlns="http://www.w3.org/2000/svg" width="1.0em" height="1.0em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="12" y1="18" x2="12" y2="12"></line><line x1="9" y1="15" x2="15" y2="15"></line></svg>`;

	/** アイコン: ゴミ箱 */
	static ICON_TRASH = `<svg xmlns="http://www.w3.org/2000/svg" width="1.0em" height="1.0em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

	/** アイコン: 複写 */
	static ICON_COPY = `<svg xmlns="http://www.w3.org/2000/svg" width="1.0em" height="1.0em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>`;

	/** オーナーエージェント名(予約名) */
	static OWNER_AGENT_NAME = "Owner";

	/** プリセットプロンプト:エージェント存在確認 */
	static PROMPT_CHECK_AGENTS = "各エージェントの稼働状況を確認してください。\n- トークンの不要な消費をさけるため、稼働状況確認以外のタスクは実行しないでください。\n- エージェント稼働確認はエージェント自身に必ず発言させてください。";

	/** プリセットプロンプト:過去会話のサマリー */
	static PROMPT_SUMMARY = "これまでの会話ログを振り返り、実施した作業の概要・現在の進捗状況・残タスクをまとめて報告してください。\n- リーダーのエージェントがこのタスクを実行してください。";

	/** プリセットプロンプト:推奨される次の指示 */
	static PROMPT_NEXT_ACTION = "現在の進捗状況を踏まえ、次に実施すべき推奨アクションを具体的に提案してください。\n- リーダーのエージェントがこのタスクを実行してください。";

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
}
