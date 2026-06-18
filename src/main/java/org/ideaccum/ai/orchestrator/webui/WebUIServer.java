package org.ideaccum.ai.orchestrator.webui;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.exception.UncheckedException;
import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.AgentConfig;
import org.ideaccum.ai.orchestrator.agent.AgentType;
import org.ideaccum.ai.orchestrator.context.Config;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.ContextFactory;
import org.ideaccum.ai.orchestrator.context.ProjectConfig;
import org.ideaccum.ai.orchestrator.context.Session;
import org.ideaccum.ai.orchestrator.processor.Orchestrator;
import org.ideaccum.ai.orchestrator.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;

/**
 * エージェント処理モニタリングWebインタフェースサーバークラスです。<br>
 * <p>
 * Java標準の{@link com.sun.net.httpserver.HttpServer}を使用した簡易的なサーバーとして提供します。<br>
 * SSE接続時はバッファ済みイベントを即座にリプレイするため、ページリロード後も状態が復元されます。<br>
 * </p>
 * <ul>
 * 	<li>{@code GET /}		HTMLモニタリング画面表示</li>
 * 	<li>{@code GET /sse}	SSEストリームリアルタイムイベント処理</li>
 * 	<li>{@code POST /prompt}	ブラウザからのプロンプト入力受付</li>
 * </ul>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/30	Kitagawa		新規作成
 *-->
 */
public class WebUIServer implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(WebUIServer.class);

	/** 環境設定オブジェクト */
	private Config config;

	/** サーバーアドレス */
	private InetSocketAddress address;

	/** HTTPサーバーインスタンス */
	private HttpServer server;

	/** コンテキストオブジェクト */
	private Context context;

	/** オーケストレーター実行エグゼキューター */
	private final ExecutorService orchestratorExecutor;

	/** オーケストレーター実行フューチャーオブジェクト */
	private volatile Future<?> orchestratorExecuteFuture;

	/** 停止シグナルフューチャーオブジェクト */
	private volatile CompletableFuture<Void> stopSignalFuture;

	/** バックグラウンドプロセス(複写・削除など非同期処理の進捗管理) */
	private volatile WebUIProcess currentBackgroundProcess;

	/**
	 * コンストラクタ<br>
	 * @param config 環境設定オブジェクト
	 */
	public WebUIServer(Config config) {
		this.config = config;
		this.address = null;
		this.server = null;
		this.context = null;
		this.orchestratorExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "orchestrator-thread");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Webインタフェースサーバーの起動を行います。<br>
	 * @throws Throwable サーバー起動時に失敗した場合にスローされます
	 */
	public void start() throws Throwable {
		address = new InetSocketAddress(config.getApplicationWebuiPort());
		server = HttpServer.create(address, config.getApplicationWebuiConnection());
		server.createContext("/", this::dispatchRequest);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		log.info("WebUIサーバーを起動しました(http://{}:{}/)。", address.getAddress().getHostAddress(), address.getPort());
	}

	/**
	 * Webインタフェースサーバーの停止を行います。<br>
	 */
	public void destroy() {
		if (stopSignalFuture != null && !stopSignalFuture.isDone()) {
			stopSignalFuture.complete(null);
		}
		orchestratorExecutor.shutdownNow();
		server.stop(0);
		server = null;
		log.info("WebUIサーバーを停止しました(http://{}:{}/)。", address.getAddress().getHostAddress(), address.getPort());
	}

	/**
	 * 停止シグナルが送出済みであるか判定します。<br>
	 * @return 停止シグナルが送出済みである場合にtrueを返却
	 */
	private boolean isStopRequested() {
		return stopSignalFuture != null && stopSignalFuture.isDone();
	}

	/**
	 * プロジェクトが選択済みであるか判定します。<br>
	 * @return プロジェクトが選択済みである場合にtrueを返却
	 */
	private boolean isProjectSelected() {
		return context != null;
	}

	/**
	 * オーケストレーターが実行中であるか判定します。<br>
	 * @return オーケストレーターが実行中である場合にtrueを返却
	 */
	private boolean isOrchestratorRunning() {
		return orchestratorExecuteFuture != null && !orchestratorExecuteFuture.isDone();
	}

	/**
	 * オーケストレーターが実行完了済みであるか判定します。<br>
	 * @return オーケストレーターが実行完了済みである場合にtrueを返却
	 */
	private boolean isOrchestratorCompleted() {
		return orchestratorExecuteFuture != null && orchestratorExecuteFuture.isDone();
	}

	/**
	 * APIレスポンスをJSON形式で送信します。<br>
	 * @param exchange リクエストレスポンス情報
	 * @param apiResponse APIレスポンスオブジェクト
	 */
	private void sendApiResponse(HttpExchange exchange, WebUIApiResponse apiResponse) {
		byte[] responseBytes = MAPPER.writeValueAsBytes(apiResponse);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.getResponseHeaders().set("Connection", "close");
		try {
			exchange.sendResponseHeaders(HTTP_STATUS_OK, responseBytes.length);
		} catch (IOException e) {
			// ステータスコードレスポンス例外は無視
			//log.error("APIレスポンス時のステータス設定例外が発生しました。", e);
		}
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(responseBytes);
		} catch (IOException e) {
			// レスポンスストリーム例外は無視(クライアントクローズの場合に必ず発生するため)
			//log.error("APIレスポンス時のストリーム出力で例外が発生しました。", e);
		}
	}

	/**
	 * リクエストのディスパッチ処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void dispatchRequest(HttpExchange exchange) throws IOException {
		try {
			String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
			if ("".equals(requestPath) || "/".equals(requestPath)) {
				// コンテキストルートはインタフェースルートにリダイレクト
				exchange.getResponseHeaders().set("Connection", "close");
				exchange.getResponseHeaders().set("Location", WEBUI_ROOT_URL);
				exchange.sendResponseHeaders(HTTP_STATUS_REDIRECT, -1);
			} else if (requestPath.startsWith(WEBUI_ROOT_URL)) {
				if (WEBUI_ROOT_URL.equals(requestPath)) {
					// インタフェースルートリクエスト
					requestWebuiRoot(exchange);
				} else if (WEBUI_THEME_CSS.equals(requestPath)) {
					// インタフェースパスリクエスト
					requestWebuiTheme(exchange);
				} else {
					// インタフェースパスリクエスト
					requestWebui(exchange);
				}
			} else if (SSE_CONNECT_URL.equals(requestPath)) {
				// SSE:接続リクエスト
				sseConnect(exchange);
			} else if (API_SELECT_PROJECT.equals(requestPath)) {
				// API:プロジェクト選択
				apiSelectProject(exchange);
			} else if (API_START_ORCHESTRATOR.equals(requestPath)) {
				// API:オーケストレーション処理開始
				apiStartOrchestrator(exchange);
			} else if (API_STOP_ORCHESTRATOR.equals(requestPath)) {
				// API:オーケストレーション処理停止
				apiStopOrchestrator(exchange);
			} else if (API_GET_ORCHESTRATOR_STATUS.equals(requestPath)) {
				// API:オーケストレーターステータス取得
				apiGetOrchestratorStatus(exchange);
			} else if (API_GET_CURRENT_PROJECT.equals(requestPath)) {
				// API:カレントプロジェクト取得
				apiGetCurrentProject(exchange);
			} else if (API_GET_PROJECT.equals(requestPath)) {
				// API:プロジェクト情報取得
				apiGetProject(exchange);
			} else if (API_SAVE_PROJECT.equals(requestPath)) {
				// API:プロジェクト情報保存
				apiSaveProject(exchange);
			} else if (API_GET_PROJECT_LIST.equals(requestPath)) {
				// API:プロジェクトリスト取得
				apiGetProjectList(exchange);
			} else if (API_GET_AGENT_LIST.equals(requestPath)) {
				// API:エージェントリスト取得
				apiGetAgentList(exchange);
			} else if (API_SAVE_AGENT.equals(requestPath)) {
				// API:エージェント保存
				apiSaveAgent(exchange);
			} else if (API_DELETE_AGENT.equals(requestPath)) {
				// API:エージェント削除
				apiDeleteAgent(exchange);
			} else if (API_DELETE_PROJECT.equals(requestPath)) {
				// API:プロジェクト削除
				apiDeleteProject(exchange);
			} else if (API_GET_CONFIG.equals(requestPath)) {
				// API:環境設定取得
				apiGetConfig(exchange);
			} else if (API_SAVE_CONFIG.equals(requestPath)) {
				// API:環境設定保存
				apiSaveConfig(exchange);
			} else if (API_GET_CONVERSATION_LOG.equals(requestPath)) {
				// API:会話ログ取得
				apiGetConversationLog(exchange);
			} else if (API_GET_DEFAULT_PROJECT.equals(requestPath)) {
				// API:プロジェクトデフォルト値取得
				apiGetDefaultProject(exchange);
			} else if (API_CLEAR_SESSION.equals(requestPath)) {
				// API:セッションクリア
				apiClearSession(exchange);
			} else if (API_GET_CONTROL_KEYWORDS.equals(requestPath)) {
				// API:制御キーワード取得
				apiGetControlKeywords(exchange);
			} else if (API_DOWNLOAD_LOGS.equals(requestPath)) {
				// API:ログダウンロード
				apiDownloadLogs(exchange);
			} else if (API_CANCEL_PROCESS.equals(requestPath)) {
				// API:バックグラウンドプロセスキャンセル
				apiCancelProcess(exchange);
			} else if (API_GET_PROCESS_STATUS.equals(requestPath)) {
				// API:バックグラウンドプロセスステータス取得
				apiGetProcessStatus(exchange);
			} else {
				exchange.sendResponseHeaders(HTTP_STATUS_FORBIDDEN, -1);
			}
		} catch (Throwable e) {
			log.error("リクエスト処理でエラーが発生しました。", e);
			sendApiResponse(exchange, WebUIApiResponse.error("サーバー内部で予期せぬエラーが発生しました。\n詳細はサーバーログに出力されています。"));
		}
	}

	/**
	 * インタフェースルートリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void requestWebuiRoot(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("インタフェースルートリクエストを受け付けました(" + requestPath + ")。");

		/*
		 * プロジェクト選択状態によるリダイレクト
		 */
		String redirectPath = isProjectSelected() ? WEBUI_PROCESS_CONTROLLER_PAGE : WEBUI_PROJECT_SETTING_PAGE;
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.getResponseHeaders().set("Location", redirectPath);
		exchange.sendResponseHeaders(HTTP_STATUS_REDIRECT, -1);
	}

	/**
	 * インタフェーステーマリソースリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void requestWebuiTheme(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("インタフェーステーマリソースリクエストを受け付けました(" + requestPath + ")。");

		/*
		 * リソースデータ読み込み
		 */
		String actualPath = WEBUI_RESOURCE_BASE + "/" + config.getApplicationTheme().getCss();
		byte[] resourceData = ResourceUtils.readResource(actualPath);

		/*
		 * Content-Type取得
		 */
		String extension = actualPath.contains(".") ? actualPath.substring(actualPath.lastIndexOf('.')) : "";
		String contentType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");

		/*
		 * ヘッダー情報設定
		 */
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.sendResponseHeaders(HTTP_STATUS_OK, resourceData.length);

		/*
		 * レスポンス出力
		 */
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(resourceData);
		} catch (IOException e) {
			// レスポンスストリーム例外は無視(クライアントクローズの場合に必ず発生するため)
			//log.error("UIテーマリソースレスポンス時のストリーム出力で例外が発生しました。", e);
		}
	}

	/**
	 * インタフェースリソースリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void requestWebui(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("インタフェースリソースリクエストを受け付けました(" + requestPath + ")。");

		/*
		 * プロジェクト未選択時のプロジェクトページリダイレクト
		 */
		if (!isProjectSelected() && requestPath.endsWith(".html") //
				&& !requestPath.endsWith(WEBUI_PROJECT_SETTING_PAGE) //
				&& !requestPath.endsWith(WEBUI_CONFIG_SETTING_PAGE) //
		) {
			exchange.getResponseHeaders().set("Connection", "close");
			exchange.getResponseHeaders().set("Location", WEBUI_PROJECT_SETTING_PAGE);
			exchange.sendResponseHeaders(HTTP_STATUS_REDIRECT, -1);
			return;
		}

		/*
		 * リソースデータ読み込み
		 */
		String actualPath = WEBUI_RESOURCE_BASE + requestPath.substring(WEBUI_BASE_URL.length());
		byte[] resourceData = ResourceUtils.readResource(actualPath);

		/*
		 * Content-Type取得
		 */
		String extension = actualPath.contains(".") ? actualPath.substring(actualPath.lastIndexOf('.')) : "";
		String contentType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");

		/*
		 * ヘッダー情報設定
		 */
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.sendResponseHeaders(HTTP_STATUS_OK, resourceData.length);

		/*
		 * レスポンス出力
		 */
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(resourceData);
		} catch (IOException e) {
			// レスポンスストリーム例外は無視(クライアントクローズの場合に必ず発生するため)
			//log.error("UIリソースレスポンス時のストリーム出力で例外が発生しました。", e);
		}
	}

	/**
	 * SSE接続リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void sseConnect(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("SSE接続リクエストを受け付けました(" + requestPath + ")。");

		/*
		 * ヘッダー情報設定
		 */
		exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(HTTP_STATUS_OK, 0);

		/*
		 * 出力ストリーム取得
		 */
		OutputStream os = exchange.getResponseBody();

		/*
		 * 処理キューイングオブジェクト
		 */
		BlockingQueue<String> queue = new LinkedBlockingQueue<>();

		/*
		 * 再接続時のバッファ済みイベントをキューに先行投入
		 */
		WebUIController.instance().getEventBuffer().forEach(queue::offer);

		/*
		 * エージェントイベントキューイングオブジェクト登録
		 */
		Consumer<String> subscriber = queue::offer;
		WebUIController.instance().subscribe(subscriber);

		/*
		 * レスポンス出力
		 */
		try {
			while (!Thread.currentThread().isInterrupted()) {
				String data = queue.poll(5, TimeUnit.SECONDS);
				if (data == null) {
					// 対象データがない場合はハートビート
					os.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
				} else {
					// データレスポンス
					os.write(data.getBytes(StandardCharsets.UTF_8));
				}
				os.flush();
			}
		} catch (IOException e) {
			// SSE入出力例外は無視(クライアントクローズの場合に必ず発生するため)
			//log.error("SSE入出力処理で例外が発生しました。", e);
		} catch (InterruptedException e) {
			// SSE停止スレッド例外は無視(内部処理の停止スレッドであるためスレッド割り込みにそのまま委譲)
			//log.error("SSE処理停止スレッドで例外が発生しました。", e);
			Thread.currentThread().interrupt();
		} finally {
			WebUIController.instance().unsubscribe(subscriber);
			try {
				os.close();
			} catch (Throwable e) {
				// SSE接続クローズに関しての例外は無視
				//log.error("SSE接続クローズで例外が発生しました。", e);
			}
		}
	}

	/**
	 * 現在選択中のプロジェクトのリーダーエージェント数を返却します。<br>
	 * @param exchange リクエストレスポンス情報
	 * @param projectName プロジェクト名
	 * @return リーダーエージェント数
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private int countLeaderAgents(HttpExchange exchange, String projectName) throws Throwable {
		Path agentsPath = config.getApplicationAgentsPath(projectName);
		if (!Files.isDirectory(agentsPath)) {
			log.error("指定されたエージェントパスがディレクトリではありません(" + agentsPath + ")。\nプロジェクト配下のエージェントパスを確認してください。");
			sendApiResponse(exchange, WebUIApiResponse.error("指定されたエージェントパスがディレクトリではありません(" + agentsPath + ")。\nプロジェクト配下のエージェントパスを確認してください。"));
		}
		try (Stream<Path> stream = Files.list(agentsPath)) {
			return (int) stream //
					.filter(file -> file.getFileName().toString().endsWith(".properties")) //
					.filter(file -> {
						Properties properties = new Properties();
						try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
							properties.load(reader);
						} catch (Throwable e) {
							throw new UncheckedException(e);
						}
						return Boolean.parseBoolean(properties.getProperty("agent.leader", "false"));
					}) //
					.count();
		} catch (Throwable e) {
			throw e;
		}
	}

	/**
	 * プロジェクト選択受付リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiSelectProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクト選択)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("projectName").asString("");
		if (isOrchestratorRunning()) {
			sendApiResponse(exchange, WebUIApiResponse.error("オーケストレーター実行中はプロジェクトを変更できません。"));
			return;
		}

		/*
		 * 処理コンテキスト生成(バックグラウンドで非同期実行)
		 */
		final String finalProjectName = projectName;

		WebUIProcess process = new WebUIProcess(false);
		currentBackgroundProcess = process;

		new Thread(() -> {
			try {
				context = new ContextFactory(config).create(finalProjectName);
				WebUIController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("project", finalProjectName);
				process.setResult(res);
				process.setStatus(WebUIProcess.STATUS_DONE);
				log.debug("プロジェクトを選択しました({})。", finalProjectName);
			} catch (Throwable e) {
				log.error("プロジェクトの選択に失敗しました({})。", finalProjectName, e);
				context = null;
				process.setMessage("プロジェクトの選択に失敗しました。");
				process.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-select-process").start();

		/*
		 * 非同期プロセス開始を即時返却
		 */
		Map<String, Object> asyncResult = new LinkedHashMap<>();
		asyncResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
	}

	/**
	 * オーケストレーター処理開始リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiStartOrchestrator(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(オーケストレーター処理開始)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String prompt = request.path("prompt").asString("");
		int leaderCount = countLeaderAgents(exchange, context.getProjectName());
		if (prompt.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("実行するタスクプロンプトを入力してください。"));
			return;
		}
		if (!isProjectSelected() || isOrchestratorRunning()) {
			sendApiResponse(exchange, WebUIApiResponse.error("オーケストレーターの処理を開始できるステータスにありません。"));
			return;
		}
		if (leaderCount == 0) {
			sendApiResponse(exchange, WebUIApiResponse.error("リーダーエージェントが設定されていないため処理を開始できません。"));
			return;
		}
		if (leaderCount > 1) {
			sendApiResponse(exchange, WebUIApiResponse.error("リーダーエージェントが複数設定されているため処理を開始できません。"));
			return;
		}

		/*
		 * コンテキストタスク設定
		 */
		context.setTask(prompt);

		/*
		 * オーナー会話エントリ追加 + SSEイベント発行
		 */
		String ownerTimestamp = DATE_FORMAT_YYYY_MM_DD_HH_MM_SS.format(new Date());
		context.getConversations().add(OWNER_AGENT_NAME, prompt, null);
		WebUIController.instance().publishStart(OWNER_AGENT_NAME, null, ownerTimestamp);
		WebUIController.instance().publishContent(OWNER_AGENT_NAME, prompt);
		WebUIController.instance().publishFinish(OWNER_AGENT_NAME);

		/*
		 * 停止シグナル初期化
		 */
		stopSignalFuture = new CompletableFuture<>();

		/*
		 * オーケストレーター処理非同期開始
		 */
		Orchestrator orchestrator = new Orchestrator(context, this::isStopRequested);
		orchestratorExecuteFuture = orchestratorExecutor.submit(() -> {
			try {
				WebUIController.instance().publishOrchestratorStarted();
				orchestrator.execute();
			} catch (Throwable e) {
				log.error("オーケストレーター実行に失敗しました。", e);
				sendApiResponse(exchange, WebUIApiResponse.error("オーケストレーターの実行に失敗しました。\n詳細はサーバーログを確認してください。"));
			}
		});

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * オーケストレーター処理停止リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiStopOrchestrator(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(オーケストレーター処理停止)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		if (!isOrchestratorRunning()) {
			sendApiResponse(exchange, WebUIApiResponse.error("オーケストレーターの処理を停止できるステータスにありません。"));
			return;
		}

		/*
		 * 停止シグナル送出
		 */
		stopSignalFuture.complete(null);

		/*
		 * SSEイベント発行
		 */
		WebUIController.instance().publishOrchestratorStopped();

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * オーケストレーターステータス取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetOrchestratorStatus(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.trace("APIリクエスト(オーケストレーターステータス取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		// なし

		/*
		 * レスポンスデータ生成
		 */
		boolean projectSelected = isProjectSelected();
		String projectName = isProjectSelected() ? context.getProjectName() : null;
		boolean running = isOrchestratorRunning();
		boolean done = isOrchestratorCompleted();
		boolean waitingStart = isProjectSelected() && !isOrchestratorRunning() && !isOrchestratorCompleted();
		int leaderCount = isProjectSelected() ? countLeaderAgents(exchange, context.getProjectName()) : 0;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("projectSelected", projectSelected);
		result.put("projectName", projectName);
		result.put("done", done);
		result.put("waitingStart", waitingStart);
		result.put("running", running);
		result.put("leaderCount", leaderCount);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * カレントプロジェクト取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetCurrentProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(カレントプロジェクト取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		// なし

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();
		result.put("project", isProjectSelected() ? context.getProjectName() : null);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクト情報取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクト情報取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト名が指定されていません。"));
			return;
		}
		if (!Files.isReadable(config.getApplicationProjectPropertiesPath(projectName))) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト設定ファイルが読み込めません。"));
			return;
		}

		/*
		 * プロジェクトプロパティ読み込み
		 */
		ProjectConfig projectConfig = new ProjectConfig(config.getApplicationProjectPropertiesPath(projectName));

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("name", projectConfig.getName());
		result.put("title", projectConfig.getTitle());
		result.put("externalEnabled", projectConfig.isExternalEnabled());
		result.put("externalPath", projectConfig.getExternalPath() != null ? projectConfig.getExternalPath() : "");

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクト情報保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiSaveProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクト情報保存)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		String originalProject = request.path("originalProject").asString(projectName);
		boolean isNew = request.path("isNew").asBoolean(false);
		String copyFromProject = request.path("copyFromProject").asString("");
		String agentTemplate = request.path("agentTemplate").asString("");
		String title = request.path("title").asString("");
		boolean externalEnabled = request.path("externalEnabled").asBoolean(false);
		String externalPath = request.path("externalPath").asString("");
		boolean isRename = !originalProject.isBlank() && !originalProject.equals(projectName);
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト名を入力してください。"));
			return;
		}
		if (!projectName.matches(ALPHANUMERIC_REGEX)) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト名は半角英数字のみ使用できます。"));
			return;
		}
		if (projectName.length() > MAX_PROJECT_NAME_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト名は" + MAX_PROJECT_NAME_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if ((isNew || isRename) && Files.exists(config.getApplicationProjectPath(projectName))) {
			sendApiResponse(exchange, WebUIApiResponse.error("同名のプロジェクトリソースが既に存在します。"));
			return;
		}
		if (isRename && !Files.exists(config.getApplicationProjectPath(originalProject))) {
			sendApiResponse(exchange, WebUIApiResponse.error("変更元のプロジェクトが見つかりません。"));
			return;
		}
		if (title.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトタイトルを入力してください。"));
			return;
		}
		if (title.length() > MAX_PROJECT_TITLE_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトタイトルは" + MAX_PROJECT_TITLE_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if (externalEnabled && externalPath.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("外部パスを使用する場合はパスを入力してください。"));
			return;
		}

		/*
		 * プロジェクトディレクトリ作成・リネーム(入出力例外などはアプリケーションエラーとしてハンドリング)
		 */
		boolean isNewProject = !isRename && !Files.exists(config.getApplicationProjectPath(projectName));
		if (isRename) {
			try {
				Files.move(config.getApplicationProjectPath(originalProject), config.getApplicationProjectPath(projectName));
			} catch (Throwable e) {
				log.error("プロジェクトディレクトリのリネームに失敗しました({} -> {})。", originalProject, projectName, e);
				sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトのリネームに失敗しました。\nファイルが使用中の可能性があります。"));
				return;
			}
			if (isProjectSelected() && originalProject.equals(context.getProjectName())) {
				try {
					context = new ContextFactory(config).create(projectName);
					WebUIController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
				} catch (Throwable e) {
					log.error("リネーム後のコンテキスト再構築に失敗しました。", e);
					context = null;
					throw e;
				}
			}
			log.debug("プロジェクトディレクトリをリネームしました({} -> {})。", originalProject, projectName);
		}
		Files.createDirectories(config.getApplicationProjectPath(projectName));
		Files.createDirectories(config.getApplicationLogPath(projectName));
		Files.createDirectories(config.getApplicationMemoryPath(projectName));
		Files.createDirectories(config.getApplicationAgentsPath(projectName));

		/*
		 * 新規プロジェクト時はテンプレートリソースをプロジェクトディレクトリへ配置(複写時はテンプレート配置をスキップ)
		 */
		if (isNewProject && copyFromProject.isBlank()) {
			ResourceUtils.copyResourceDirectory(RESOURCE_TEMPLATE_PROJECT, config.getApplicationProjectPath(projectName));
			log.debug("プロジェクトテンプレートを配置しました({})。", config.getApplicationProjectPath(projectName));

			String templateAgentsResource = null;
			if ("fullstack".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_FULLSTACK;
			} else if ("middle".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_MIDDLE;
			} else if ("light_claude".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_LIGHT_CLAUDE;
			} else if ("light_codex".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_LIGHT_CODEX;
			} else if ("light_gemini".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_LIGHT_GEMINI;
			} else if ("discussion".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_DISCUSSION;
			} else if ("programming".equals(agentTemplate)) {
				templateAgentsResource = RESOURCE_TEMPLATE_AGENTS_PROGRAMMING;
			}
			if (templateAgentsResource != null) {
				ResourceUtils.copyResourceDirectory(templateAgentsResource, config.getApplicationAgentsPath(projectName));
				log.debug("エージェントテンプレートを配置しました({} -> {})。", templateAgentsResource, config.getApplicationAgentsPath(projectName));
			}
		}

		/*
		 * 複写時はコピー元プロジェクトをバックグラウンドスレッドで非同期コピー(キャンセル対応)
		 * (.orchestrator配下はagentsディレクトリのみコピー)
		 */
		if (isNewProject && !copyFromProject.isBlank()) {
			final String finalProjectName = projectName;
			final String finalCopyFromProject = copyFromProject;
			final String finalTitle = title;
			final boolean finalExternalEnabled = externalEnabled;
			final String finalExternalPath = externalPath;
			final Path sourcePath = config.getApplicationProjectPath(copyFromProject);
			final Path destPath = config.getApplicationProjectPath(projectName);
			final Path sourceAgentsPath = config.getApplicationAgentsPath(copyFromProject);
			final Path sourceOrchestratorPath = sourceAgentsPath.getParent();

			WebUIProcess task = new WebUIProcess(true);
			currentBackgroundProcess = task;

			new Thread(() -> {
				try {
					if (Files.isDirectory(sourcePath)) {
						List<Path> paths;
						try (Stream<Path> walk = Files.walk(sourcePath)) {
							paths = walk.collect(Collectors.toList());
						}
						for (Path src : paths) {
							if (task.isCancelled()) {
								break;
							}
							if (src.startsWith(sourceOrchestratorPath) && !src.equals(sourceOrchestratorPath) && !src.startsWith(sourceAgentsPath)) {
								continue;
							}
							Path dest = destPath.resolve(sourcePath.relativize(src));
							if (Files.isDirectory(src)) {
								Files.createDirectories(dest);
							} else {
								Files.createDirectories(dest.getParent());
								Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}

					if (task.isCancelled()) {
						// 複写先ディレクトリを削除してキャンセル状態に設定
						if (Files.exists(destPath)) {
							try (Stream<Path> walk = Files.walk(destPath)) {
								walk.sorted(Comparator.reverseOrder()).forEach(p -> {
									try {
										Files.delete(p);
									} catch (IOException ignored) {
									}
								});
							}
						}
						log.debug("プロジェクトの複写を中断しました({} -> {})。", finalCopyFromProject, finalProjectName);
						task.setMessage("複写を中断しました。");
						task.setStatus(WebUIProcess.STATUS_CANCELLED);
					} else {
						// プロジェクトプロパティ保存
						ProjectConfig pc = new ProjectConfig(config.getApplicationProjectPropertiesPath(finalProjectName));
						pc.setName(finalProjectName);
						pc.setTitle(finalTitle);
						pc.setExternalEnabled(finalExternalEnabled);
						pc.setExternalPath(finalExternalPath);
						pc.save(config.getApplicationProjectPropertiesPath(finalProjectName));
						log.debug("プロジェクトを複写しました({} -> {})。", finalCopyFromProject, finalProjectName);
						Map<String, Object> res = new LinkedHashMap<>();
						res.put("savedName", finalProjectName);
						task.setResult(res);
						task.setStatus(WebUIProcess.STATUS_DONE);
					}
				} catch (Throwable e) {
					log.warn("プロジェクトの複写に失敗しました({} -> {})。", finalCopyFromProject, finalProjectName, e);
					task.setMessage("プロジェクトの複写に失敗しました。ファイルが使用中の可能性があります。");
					task.setStatus(WebUIProcess.STATUS_ERROR);
				}
			}, "background-copy-process").start();

			/*
			 * 非同期タスク開始を即時返却
			 */
			Map<String, Object> asyncResult = new LinkedHashMap<>();
			asyncResult.put("isAsync", true);
			sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
			return;
		}

		/*
		 * プロジェクトプロパティ保存(複写以外の通常保存・バックグラウンドで非同期実行)
		 */
		final String finalProjectName = projectName;
		final String finalTitle = title;
		final boolean finalExternalEnabled = externalEnabled;
		final String finalExternalPath = externalPath;

		WebUIProcess process = new WebUIProcess(false);
		currentBackgroundProcess = process;

		new Thread(() -> {
			try {
				ProjectConfig pc = new ProjectConfig(config.getApplicationProjectPropertiesPath(finalProjectName));
				pc.setName(finalProjectName);
				pc.setTitle(finalTitle);
				pc.setExternalEnabled(finalExternalEnabled);
				pc.setExternalPath(finalExternalPath);
				pc.save(config.getApplicationProjectPropertiesPath(finalProjectName));
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("savedName", finalProjectName);
				res.put("message", "プロジェクトを登録しました。");
				process.setResult(res);
				process.setStatus(WebUIProcess.STATUS_DONE);
				log.debug("プロジェクトを登録しました({})。", finalProjectName);
			} catch (Throwable e) {
				log.error("プロジェクトの保存に失敗しました({})。", finalProjectName, e);
				process.setMessage("プロジェクトの保存に失敗しました。");
				process.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-save-process").start();

		/*
		 * 非同期プロセス開始を即時返却
		 */
		Map<String, Object> asyncResult = new LinkedHashMap<>();
		asyncResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
	}

	/**
	 * プロジェクト一覧取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetProjectList(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクト一覧取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		// なし

		/*
		 * レスポンスデータ生成
		 */
		List<Map<String, Object>> result = new ArrayList<>();
		for (String projectName : config.getApplicationProjectNames()) {
			ProjectConfig projectConfig = new ProjectConfig(config.getApplicationProjectPropertiesPath(projectName));
			Map<String, Object> project = new LinkedHashMap<>();
			project.put("name", projectName);
			project.put("title", projectConfig.getTitle());
			project.put("externalEnabled", projectConfig.isExternalEnabled());
			project.put("externalPath", projectConfig.getExternalPath() != null ? projectConfig.getExternalPath() : "");

			Path convFile = config.getAgentConversationLogfile(projectName);
			boolean hasConversations = false;
			int turnCount = 0;
			long totalTokens = 0;
			long durationSec = 0;
			Map<String, Long> agentTokenMap = new LinkedHashMap<>();
			if (Files.exists(convFile)) {
				try {
					JsonNode convNode = MAPPER.readTree(convFile.toFile());
					JsonNode entriesNode = convNode.isArray() ? convNode : convNode.path("entries");
					if (entriesNode.isArray() && !entriesNode.isEmpty()) {
						hasConversations = true;
						String firstTimestamp = null;
						String lastTimestamp = null;
						for (JsonNode entry : entriesNode) {
							String agentName = entry.path("agentName").asString("");
							String timestamp = entry.path("timestamp").asString("");
							if (!timestamp.isEmpty()) {
								if (firstTimestamp == null) {
									firstTimestamp = timestamp;
								}
								lastTimestamp = timestamp;
							}
							if (!OWNER_AGENT_NAME.equals(agentName)) {
								turnCount++;
								JsonNode tokenUsage = entry.path("tokenUsage");
								long ioTokens = tokenUsage.path("inputTokens").asLong(0) + tokenUsage.path("outputTokens").asLong(0);
								totalTokens += ioTokens;
								agentTokenMap.merge(agentName, ioTokens, Long::sum);
							}
						}
						if (firstTimestamp != null && lastTimestamp != null && !firstTimestamp.equals(lastTimestamp)) {
							try {
								long first = DATE_FORMAT_YYYY_MM_DD_HH_MM_SS.parse(firstTimestamp).getTime();
								long last = DATE_FORMAT_YYYY_MM_DD_HH_MM_SS.parse(lastTimestamp).getTime();
								durationSec = (last - first) / 1000;
							} catch (Throwable ignored) {
							}
						}
					}
				} catch (Throwable ignored) {
				}
			}
			// エージェントごとにtype/modelを付加したリストを構築
			List<Map<String, Object>> agentTokens = new ArrayList<>();
			for (Map.Entry<String, Long> e : agentTokenMap.entrySet()) {
				String agentName = e.getKey();
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("name", agentName);
				entry.put("tokens", e.getValue());
				Path agentFile = config.getApplicationAgentsPath(projectName).resolve(agentName + ".properties");
				if (Files.exists(agentFile)) {
					Properties agentProps = new Properties();
					try (Reader agentReader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(agentFile)), StandardCharsets.UTF_8)) {
						agentProps.load(agentReader);
					} catch (Throwable ignored) {
					}
					entry.put("type", agentProps.getProperty("agent.type", ""));
					entry.put("model", agentProps.getProperty("agent.model", ""));
				} else {
					entry.put("type", "");
					entry.put("model", "");
				}
				agentTokens.add(entry);
			}
			project.put("hasConversations", hasConversations);
			project.put("turnCount", turnCount);
			project.put("totalTokens", totalTokens);
			project.put("durationSec", durationSec);
			project.put("agentTokens", agentTokens);
			result.add(project);
		}

		/*
		 * アクティブプロジェクトを先頭へ
		 */
		if (isProjectSelected()) {
			String activeName = context.getProjectName();
			result.sort(Comparator.comparingInt(project -> activeName.equals(project.get("name")) ? 0 : 1));
		}

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * エージェント一覧取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetAgentList(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(エージェント一覧取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}

		/*
		 * レスポンスデータ生成
		 */
		List<Map<String, Object>> result = new ArrayList<>();
		Path agentsPath = config.getApplicationAgentsPath(projectName);
		if (Files.isDirectory(agentsPath)) {
			try (Stream<Path> stream = Files.list(agentsPath)) {
				List<Path> files = stream //
						.filter(file -> file.getFileName().toString().endsWith(".properties")) //
						.filter(file -> !file.getFileName().toString().equalsIgnoreCase(OWNER_AGENT_NAME + ".properties")) //
						.sorted(Comparator.comparing(file -> file.getFileName().toString())) //
						.collect(Collectors.toList());
				for (Path file : files) {
					Properties properties = new Properties();
					try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
						properties.load(reader);
					}
					String agentName = properties.getProperty("agent.name", "");
					Map<String, Object> agent = new LinkedHashMap<>();
					agent.put("name", agentName);
					agent.put("type", properties.getProperty("agent.type", AGENT_DEFAULT_TYPE));
					agent.put("model", properties.getProperty("agent.model", ""));
					agent.put("extraArgs", properties.getProperty("agent.extra.args", ""));
					agent.put("leader", Boolean.parseBoolean(properties.getProperty("agent.leader", "false")));
					agent.put("role", properties.getProperty("agent.role", ""));
					String sessionId = "";
					boolean inConversation = false;
					if (isProjectSelected() && projectName.equals(context.getProjectName())) {
						Session session = context.getSessions().get(agentName);
						sessionId = session != null && session.getSessionId() != null ? session.getSessionId() : "";
						final String finalAgentName = agentName;
						inConversation = context.getConversations().getAll().stream().anyMatch(c -> finalAgentName.equals(c.getAgentName()));
					}
					agent.put("sessionId", sessionId);
					agent.put("inConversation", inConversation);
					agent.put("personality", properties.getProperty("agent.personality", ""));
					result.add(agent);
				}
			}
		}

		/*
		 * リーダーエージェントを先頭へ
		 */
		result.sort(Comparator.comparingInt(agent -> Boolean.TRUE.equals(agent.get("leader")) ? 0 : 1));

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * エージェント保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiSaveAgent(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(エージェント保存)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		String originalName = request.path("originalName").asString("");
		boolean isNewAgent = request.path("isNew").asBoolean(false);
		String name = request.path("name").asString("");
		String type = request.path("type").asString(AGENT_DEFAULT_TYPE);
		String model = request.path("model").asString("");
		String extraArgs = request.path("extraArgs").asString("");
		boolean leader = request.path("leader").asBoolean(false);
		String role = request.path("role").asString("");
		String personality = request.path("personality").asString("");
		String filename = name + ".properties";
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}
		if (name.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("エージェント名を入力してください。"));
			return;
		}
		if (OWNER_AGENT_NAME.equalsIgnoreCase(name)) {
			sendApiResponse(exchange, WebUIApiResponse.error("\"" + OWNER_AGENT_NAME + "\" はシステム予約名のため使用できません。"));
			return;
		}
		if (!name.matches(ALPHANUMERIC_REGEX)) {
			sendApiResponse(exchange, WebUIApiResponse.error("エージェント名は半角英数字のみ使用できます。"));
			return;
		}
		if (name.length() > MAX_AGENT_NAME_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("エージェント名は" + MAX_AGENT_NAME_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if (model.length() > MAX_MODEL_NAME_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("モデルは" + MAX_MODEL_NAME_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if (extraArgs.length() > MAX_EXTRA_ARGS_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("追加引数は" + MAX_EXTRA_ARGS_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if (role.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("役割名を入力してください。"));
			return;
		}
		if (role.length() > MAX_AGENT_ROLE_LENGTH) {
			sendApiResponse(exchange, WebUIApiResponse.error("役割名は" + MAX_AGENT_ROLE_LENGTH + "文字以内で入力してください。"));
			return;
		}
		if (personality.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("性質を入力してください。"));
			return;
		}
		boolean isAgentRename = !originalName.isBlank() && !originalName.equals(name);
		if (isNewAgent && Files.exists(config.getApplicationAgentsPath(projectName).resolve(filename))) {
			sendApiResponse(exchange, WebUIApiResponse.error("同名のエージェントが既に存在します。"));
			return;
		}
		if (isAgentRename && Files.exists(config.getApplicationAgentsPath(projectName).resolve(filename))) {
			sendApiResponse(exchange, WebUIApiResponse.error("同名のエージェントが既に存在します。"));
			return;
		}
		if (!isNewAgent && isProjectSelected() && projectName.equals(context.getProjectName())) {
			String lookupName = originalName.isBlank() ? name : originalName;
			final String finalLookup = lookupName;
			boolean inConversation = context.getConversations().getAll().stream().anyMatch(c -> finalLookup.equals(c.getAgentName()));
			if (inConversation) {
				sendApiResponse(exchange, WebUIApiResponse.error("既に会話に参加しているため、変更することはできません。"));
				return;
			}
		}
		if (!isNewAgent && isProjectSelected() && projectName.equals(context.getProjectName())) {
			String lookupName = originalName.isBlank() ? name : originalName;
			Session session = context.getSessions().get(lookupName);
			if (session != null && session.getSessionId() != null && !session.getSessionId().isBlank()) {
				Path originalFile = config.getApplicationAgentsPath(projectName).resolve(lookupName + ".properties");
				if (Files.exists(originalFile)) {
					Properties originalProps = new Properties();
					try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(originalFile)), StandardCharsets.UTF_8)) {
						originalProps.load(reader);
					}
					if (!type.equals(originalProps.getProperty("agent.type", AGENT_DEFAULT_TYPE))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!model.equals(Objects.toString(originalProps.getProperty("agent.model"), ""))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!extraArgs.equals(Objects.toString(originalProps.getProperty("agent.extra.args"), ""))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (leader != Boolean.parseBoolean(originalProps.getProperty("agent.leader", "false"))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!role.equals(Objects.toString(originalProps.getProperty("agent.role"), ""))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!personality.equals(Objects.toString(originalProps.getProperty("agent.personality"), ""))) {
						sendApiResponse(exchange, WebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
				}
			}
		}

		/*
		* エージェントプロパティ保存・コンテキスト再構築(バックグラウンドで非同期実行)
		*/
		final String finalProjectName = projectName;
		final String finalAgentName = name;
		final String finalOriginalName = originalName;
		final String finalFilename = filename;
		final boolean finalIsAgentRename = isAgentRename;
		final AgentConfig agentConfig = new AgentConfig();
		agentConfig.setName(name);
		agentConfig.setType(type);
		agentConfig.setModel(model);
		agentConfig.setLeader(leader);
		agentConfig.setRole(role);
		agentConfig.setPersonality(personality);
		agentConfig.setExtraArgs(List.of(extraArgs.split("\s+")));

		WebUIProcess process = new WebUIProcess(false);
		currentBackgroundProcess = process;

		new Thread(() -> {
			try {
				Path agentsPath = config.getApplicationAgentsPath(finalProjectName);
				Files.createDirectories(agentsPath);
				agentConfig.save(agentsPath.resolve(finalFilename));
				if (finalIsAgentRename) {
					Files.deleteIfExists(agentsPath.resolve(finalOriginalName + ".properties"));
					if (isProjectSelected() && finalProjectName.equals(context.getProjectName())) {
						context.getSessions().rename(finalOriginalName, finalAgentName);
					}
				}
				if (isProjectSelected() && finalProjectName.equals(context.getProjectName()) && !isOrchestratorRunning()) {
					try {
						context = new ContextFactory(config).create(finalProjectName);
						WebUIController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
					} catch (Throwable e) {
						log.warn("エージェント保存後のコンテキスト再構築に失敗しました。", e);
					}
				}
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("name", finalAgentName);
				process.setResult(res);
				process.setStatus(WebUIProcess.STATUS_DONE);
				log.debug("エージェントを保存しました({}/{})。", finalProjectName, finalAgentName);
			} catch (Throwable e) {
				log.error("エージェントの保存に失敗しました({}/{})。", finalProjectName, finalAgentName, e);
				process.setMessage("エージェントの保存に失敗しました。");
				process.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-save-agent-process").start();

		Map<String, Object> asyncResult = new LinkedHashMap<>();
		asyncResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
	}

	/**
	 * エージェント削除APIリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiDeleteAgent(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(エージェント削除)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		String name = request.path("name").asString("");
		String filename = name + ".properties";
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}
		if (name.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("エージェント名が指定されていません。"));
			return;
		}
		if (isProjectSelected() && projectName.equals(context.getProjectName())) {
			final String finalName = name;
			boolean inConversation = context.getConversations().getAll().stream().anyMatch(c -> finalName.equals(c.getAgentName()));
			if (inConversation) {
				sendApiResponse(exchange, WebUIApiResponse.error("既に会話に参加しているため、変更することはできません。"));
				return;
			}
		}

		/*
		* エージェントプロパティ削除・コンテキスト再構築(バックグラウンドで非同期実行)
		*/
		final String finalProjectName = projectName;
		final String finalAgentName = name;
		final String finalFilename = filename;

		WebUIProcess process = new WebUIProcess(false);
		currentBackgroundProcess = process;

		new Thread(() -> {
			try {
				Path agentsPath = config.getApplicationAgentsPath(finalProjectName);
				Files.deleteIfExists(agentsPath.resolve(finalFilename));
				if (isProjectSelected() && finalProjectName.equals(context.getProjectName()) && !isOrchestratorRunning()) {
					try {
						context = new ContextFactory(config).create(finalProjectName);
						WebUIController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
					} catch (Throwable e) {
						log.warn("エージェント削除後のコンテキスト再構築に失敗しました。", e);
					}
				}
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("deletedName", finalAgentName);
				process.setResult(res);
				process.setStatus(WebUIProcess.STATUS_DONE);
				log.debug("エージェントを削除しました({}/{})。", finalProjectName, finalAgentName);
			} catch (Throwable e) {
				log.error("エージェントの削除に失敗しました({}/{})。", finalProjectName, finalAgentName, e);
				process.setMessage("エージェントの削除に失敗しました。");
				process.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-delete-agent-process").start();

		Map<String, Object> asyncResult = new LinkedHashMap<>();
		asyncResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
	}

	/**
	 * プロジェクト削除リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiDeleteProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクト削除)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String projectName = request.path("project").asString("");
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクト名が指定されていません。"));
			return;
		}

		/*
		 * プロジェクトディレクトリ削除(バックグラウンドスレッドで非同期処理・キャンセル対応)
		 */
		final String finalProjectName = projectName;
		final Path projectPath = config.getApplicationProjectPath(projectName);
		final boolean wasActive = isProjectSelected() && projectName.equals(context != null ? context.getProjectName() : null);

		WebUIProcess task = new WebUIProcess(true);
		currentBackgroundProcess = task;

		new Thread(() -> {
			try {
				if (Files.exists(projectPath)) {
					List<Path> paths;
					try (Stream<Path> walk = Files.walk(projectPath)) {
						paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
					}
					for (Path p : paths) {
						if (task.isCancelled()) {
							break;
						}
						try {
							Files.delete(p);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
				}

				if (task.isCancelled()) {
					log.warn("プロジェクトの削除を中断しました({})。", finalProjectName);
					task.setMessage("削除を中断しました。ファイルが残存している可能性があります。");
					task.setStatus(WebUIProcess.STATUS_CANCELLED);
				} else {
					// 削除対象がアクティブプロジェクトの場合はコンテキストをクリア
					if (wasActive) {
						context = null;
						orchestratorExecuteFuture = null;
						stopSignalFuture = null;
						log.debug("アクティブプロジェクトが削除されたためコンテキストをクリアしました({})。", finalProjectName);
					}
					Map<String, Object> res = new LinkedHashMap<>();
					res.put("deletedName", finalProjectName);
					res.put("wasActive", wasActive);
					task.setResult(res);
					task.setStatus(WebUIProcess.STATUS_DONE);
					log.debug("プロジェクトを削除しました({})。", finalProjectName);
				}
			} catch (UncheckedIOException e) {
				log.warn("プロジェクトディレクトリの削除に失敗しました({})。", finalProjectName, e.getCause());
				task.setMessage("プロジェクトの削除に失敗しました。ファイルが使用中の可能性があります。");
				task.setStatus(WebUIProcess.STATUS_ERROR);
			} catch (Throwable e) {
				log.warn("プロジェクトディレクトリの削除に失敗しました({})。", finalProjectName, e);
				task.setMessage("プロジェクトの削除中に予期せぬエラーが発生しました。");
				task.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-delete-process").start();

		/*
		 * 非同期タスク開始を即時返却
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * 環境設定取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetConfig(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(環境設定取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("config", config.toConfigMap());

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * 環境設定保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiSaveConfig(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(環境設定保存)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		String repositoryPath = request.path("repositoryPath").asString("").trim();
		String webuiPort = request.path("webuiPort").asString("").trim();
		String webuiConnection = request.path("webuiConnection").asString("").trim();
		String agentTimeout = request.path("agentTimeout").asString("").trim();
		if (repositoryPath.isEmpty()) {
			sendApiResponse(exchange, WebUIApiResponse.error("リポジトリパスを入力してください。"));
			return;
		}
		try {
			int port = Integer.parseInt(webuiPort);
			if (port < 1 || port > MAX_PORT_NUMBER) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, WebUIApiResponse.error("WebUIポートには1～65535の整数を入力してください。"));
			return;
		}
		try {
			if (Integer.parseInt(webuiConnection) < 1) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, WebUIApiResponse.error("WebUI同時接続数には1以上の整数を入力してください。"));
			return;
		}
		try {
			if (Integer.parseInt(agentTimeout) < 1) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, WebUIApiResponse.error("エージェントタイムアウトには1以上の整数を入力してください。"));
			return;
		}

		/*
		* Configセッターで値を更新(バックグラウンドで非同期保存)
		*/
		config.setApplicationTheme(request.path("theme").asString("").trim());
		config.setApplicationRepositoryPath(repositoryPath);
		config.setApplicationWebuiPort(Integer.parseInt(webuiPort));
		config.setApplicationWebuiConnection(Integer.parseInt(webuiConnection));
		config.setAgentTimeout(Integer.parseInt(agentTimeout));
		config.setAgentCliCommand(AgentType.CLAUDE_CLI.getName(), request.path("cliClaudeCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.GEMINI_CLI.getName(), request.path("cliGeminiCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.ANTIGRAVITY_CLI.getName(), request.path("cliAntigravityCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.CODEX_CLI.getName(), request.path("cliCodexCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.COPILOT_CLI.getName(), request.path("cliCopilotCommand").asString("").trim());

		WebUIProcess cfgProcess = new WebUIProcess(false);
		currentBackgroundProcess = cfgProcess;

		new Thread(() -> {
			try {
				config.save();
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("message", "環境設定を保存しました。\nポートや接続数の変更はサーバー再起動後に反映されます。");
				cfgProcess.setResult(res);
				cfgProcess.setStatus(WebUIProcess.STATUS_DONE);
				log.debug("環境設定を保存しました。");
			} catch (Throwable e) {
				log.error("環境設定の保存に失敗しました。", e);
				cfgProcess.setMessage("環境設定の保存に失敗しました。");
				cfgProcess.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-save-config-process").start();

		Map<String, Object> asyncCfgResult = new LinkedHashMap<>();
		asyncCfgResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncCfgResult, null));
	}

	/**
	 * 会話ログ取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetConversationLog(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(会話ログ取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * バリデーションチェック
		 */
		if (!isProjectSelected()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}

		/*
		 * レスポンスデータ生成
		 */
		Path logFile = config.getAgentConversationLogfile(context.getProjectName());
		List<Map<String, Object>> entries = new ArrayList<>();
		if (Files.exists(logFile)) {
			JsonNode root = MAPPER.readTree(logFile.toFile());
			/*
			 * 直配列形式([{...},...]) と entries包含形式({"entries":[...]}) の両方に対応
			 */
			JsonNode entriesNode = root.isArray() ? root : root.path("entries");
			if (entriesNode.isArray()) {
				for (JsonNode entry : entriesNode) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("timestamp", entry.path("timestamp").asString(""));
					item.put("agentName", entry.path("agentName").asString(""));
					item.put("content", entry.path("content").asString(""));
					JsonNode tu = entry.path("tokenUsage");
					Map<String, Object> tokenUsage = new LinkedHashMap<>();
					tokenUsage.put("inputTokens", tu.path("inputTokens").asLong(0));
					tokenUsage.put("outputTokens", tu.path("outputTokens").asLong(0));
					tokenUsage.put("otherTokens", tu.path("otherTokens").asLong(0));
					item.put("tokenUsage", tokenUsage);
					entries.add(item);
				}
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("projectName", context.getProjectName());
		result.put("conversations", entries);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * セッションクリアリクエスト時の処理を行います。<br>
	 * ログディレクトリ・会話ファイル・セッションファイルを削除します。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiClearSession(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(セッションクリア)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);

		String projectName = request.path("project").asString("");
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが指定されていません。"));
			return;
		}
		if (isOrchestratorRunning()) {
			sendApiResponse(exchange, WebUIApiResponse.error("オーケストレーター実行中はセッションをクリアできません。"));
			return;
		}

		/*
		 * ファイル削除・コンテキスト再構築(バックグラウンドで非同期実行)
		 */
		final String finalProjectName = projectName;
		final boolean wasActive = isProjectSelected() && projectName.equals(context != null ? context.getProjectName() : null);

		WebUIProcess process = new WebUIProcess(false);
		currentBackgroundProcess = process;

		new Thread(() -> {
			try {
				/*
				 * ログディレクトリ削除
				 */
				Path logsPath = config.getApplicationLogPath(finalProjectName);
				if (Files.exists(logsPath)) {
					try (Stream<Path> walk = Files.walk(logsPath)) {
						walk.sorted(Comparator.reverseOrder()).forEach(p -> {
							try {
								Files.delete(p);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
					}
				}

				/*
				 * メモリディレクトリ削除
				 */
				Path memoryPath = config.getApplicationMemoryPath(finalProjectName);
				if (Files.exists(memoryPath)) {
					try (Stream<Path> walk = Files.walk(memoryPath)) {
						walk.sorted(Comparator.reverseOrder()).forEach(p -> {
							try {
								Files.delete(p);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
					}
				}

				/*
				 * 会話・セッションファイル削除
				 */
				Files.deleteIfExists(config.getAgentConversationLogfile(finalProjectName));
				Files.deleteIfExists(config.getAgentSessionStore(finalProjectName));

				/*
				 * アクティブプロジェクトの場合はコンテキスト再構築
				 */
				if (wasActive) {
					try {
						context = new ContextFactory(config).create(finalProjectName);
						WebUIController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
					} catch (Throwable e) {
						log.warn("セッションクリア後のコンテキスト再構築に失敗しました。", e);
					}
				}

				log.debug("セッション情報をクリアしました({})。", finalProjectName);
				Map<String, Object> res = new LinkedHashMap<>();
				res.put("project", finalProjectName);
				res.put("message", "セッション情報をクリアしました。");
				process.setResult(res);
				process.setStatus(WebUIProcess.STATUS_DONE);
			} catch (Throwable e) {
				log.warn("セッションのクリアに失敗しました({})。", finalProjectName, e);
				process.setMessage("セッションのクリアに失敗しました。ファイルが使用中の可能性があります。");
				process.setStatus(WebUIProcess.STATUS_ERROR);
			}
		}, "background-clear-session-process").start();

		/*
		 * 非同期プロセス開始を即時返却
		 */
		Map<String, Object> asyncResult = new LinkedHashMap<>();
		asyncResult.put("isAsync", true);
		sendApiResponse(exchange, WebUIApiResponse.ok(asyncResult, null));
	}

	/**
	 * 制御キーワード取得リクエスト時の処理を行います。<br>
	 * ディスパッチ・完了・中断の各キーワード文字列を返却します。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetControlKeywords(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(制御キーワード取得)を受け付けました(" + requestPath + ")。");

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("finalizeKeyword", AGENT_FINALIZE_KEYWORD);
		result.put("interruptKeyword", AGENT_INTERRRUPTE_KEYWORD);
		result.put("dispatchRegexp", AGENT_DISPATH_REGEX);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

	/**
	 * ログダウンロードリクエスト時の処理を行います。<br>
	 * プロジェクトの .orchestrator ディレクトリ配下を ZIP 圧縮してレスポンスします。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiDownloadLogs(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(ログダウンロード)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		String projectName = request.path("project").asString("");
		if (projectName.isBlank()) {
			sendApiResponse(exchange, WebUIApiResponse.error("プロジェクトが指定されていません。"));
			return;
		}

		Path orchestratorPath = config.getApplicationProjectPath(projectName).resolve(ORCHESTRATOR_HOME_PATH);
		if (!Files.exists(orchestratorPath)) {
			sendApiResponse(exchange, WebUIApiResponse.error("ログデータが存在しません。"));
			return;
		}

		/*
		 * ファイル名生成: {projectName}_yyyyMMdd-HHmmss.zip
		 */
		String timestamp = DATE_FORMAT_YYYYMMDD_HHMMSS.format(new Date());
		String fileName = projectName + "_" + timestamp + ".zip";

		/*
		 * ZIP 圧縮
		 */
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			Files.walk(orchestratorPath).filter(p -> !Files.isDirectory(p)).forEach(p -> {
				try {
					String entryName = orchestratorPath.getParent().relativize(p).toString().replace("\\", "/");
					zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
					Files.copy(p, zos);
					zos.closeEntry();
				} catch (IOException e) {
					log.error("ZIPエントリ追加でエラーが発生しました(" + p + ")。", e);
				}
			});
		} catch (Throwable e) {
			log.error("ZIP圧縮でエラーが発生しました。", e);
			sendApiResponse(exchange, WebUIApiResponse.error("ZIP圧縮処理でエラーが発生しました。"));
			return;
		}

		/*
		 * バイナリレスポンス返却
		 */
		byte[] zipBytes = baos.toByteArray();
		exchange.getResponseHeaders().set("Content-Type", "application/zip");
		exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.sendResponseHeaders(HTTP_STATUS_OK, zipBytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(zipBytes);
		} catch (IOException e) {
			// レスポンスcloseの例外は無視
		}
	}

	/**
	 * バックグラウンドプロセスキャンセルリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiCancelProcess(HttpExchange exchange) throws IOException {
		log.debug("APIリクエスト(バックグラウンドプロセスキャンセル)を受け付けました。");
		WebUIProcess task = currentBackgroundProcess;
		if (task == null || !WebUIProcess.STATUS_RUNNING.equals(task.getStatus())) {
			sendApiResponse(exchange, WebUIApiResponse.error("実行中のタスクが存在しません。"));
			return;
		}
		if (!task.isCancellable()) {
			sendApiResponse(exchange, WebUIApiResponse.error("このタスクはキャンセルできません。"));
			return;
		}
		task.cancel();
		sendApiResponse(exchange, WebUIApiResponse.ok(null, "中断要求を受け付けました。"));
	}

	/**
	 * バックグラウンドプロセスステータス取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetProcessStatus(HttpExchange exchange) throws IOException {
		log.debug("APIリクエスト(バックグラウンドプロセスステータス取得)を受け付けました。");
		WebUIProcess task = currentBackgroundProcess;
		Map<String, Object> data = new LinkedHashMap<>();
		if (task == null) {
			data.put("status", "idle");
		} else {
			data.put("status", task.getStatus());
			data.put("cancellable", task.isCancellable());
			if (task.getMessage() != null) {
				data.put("message", task.getMessage());
			}
			if (task.getResult() != null) {
				data.put("result", task.getResult());
			}
		}
		sendApiResponse(exchange, WebUIApiResponse.ok(data));
	}

	/**
	 * プロジェクトデフォルト値取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws Throwable 処理中に予期せぬ例外が発生した場合にスローされます
	 */
	private void apiGetDefaultProject(HttpExchange exchange) throws Throwable {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("APIリクエスト(プロジェクトデフォルト値取得)を受け付けました(" + requestPath + ")。");

		/*
		 * リクエスト情報取得
		 */
		byte[] requestBytes = exchange.getRequestBody().readAllBytes();
		JsonNode request = MAPPER.readTree(requestBytes);
		log.trace("リクエスト情報 : " + request.toString());

		/*
		 * デフォルトプロジェクト設定読み込み
		 */
		Properties defaults = new Properties();
		try (Reader reader = new InputStreamReader(new BufferedInputStream(WebUIServer.class.getResourceAsStream(DEFAULT_PROJECT_FILE)), StandardCharsets.UTF_8)) {
			defaults.load(reader);
		} catch (Exception e) {
			log.warn("デフォルトプロジェクト設定ファイルの読み込みに失敗しました。", e);
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("name", defaults.getProperty("project.name", ""));
		result.put("title", defaults.getProperty("project.title", ""));

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, WebUIApiResponse.ok(result));
	}

}
