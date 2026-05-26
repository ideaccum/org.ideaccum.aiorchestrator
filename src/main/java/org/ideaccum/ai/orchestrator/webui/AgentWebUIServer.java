package org.ideaccum.ai.orchestrator.webui;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
public class AgentWebUIServer implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(AgentWebUIServer.class);

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

	/**
	 * コンストラクタ<br>
	 * @param config 環境設定オブジェクト
	 */
	public AgentWebUIServer(Config config) {
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
	 * @throws IOException レスポンス送信に失敗した場合にスローされます
	 */
	private void sendApiResponse(HttpExchange exchange, AgentWebUIApiResponse apiResponse) throws IOException {
		byte[] responseBytes = MAPPER.writeValueAsBytes(apiResponse);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.getResponseHeaders().set("Connection", "close");
		exchange.sendResponseHeaders(200, responseBytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(responseBytes);
		} catch (IOException e) {
			// レスポンスcloseの例外は無視
		}
	}

	/**
	 * エージェント指示行を除いたコンテンツを提供します。<br>
	 * @param content コンテンツ内容
	 * @return エージェント指示行を除いたコンテンツ内容
	 */
	private String removeIstructLine(String content) {
		StringBuilder builder = new StringBuilder();
		content.lines().forEach(line -> {
			if (AGENT_FINALIZE_KEYWORD.equals(line)) {
				return;
			}
			if (AGENT_INTERRRUPTE_KEYWORD.equals(line)) {
				return;
			}
			if (AGENT_DISPATH_REGEXP.matcher(line).find()) {
				return;
			}
			builder.append(line).append("\n");
		});
		return builder.toString();
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
				exchange.sendResponseHeaders(302, -1);
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
			} else {
				exchange.sendResponseHeaders(403, -1);
			}
		} catch (Throwable e) {
			log.error("リクエスト処理でエラーが発生しました。", e);
		}
	}

	/**
	 * インタフェースルートリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void requestWebuiRoot(HttpExchange exchange) throws IOException {
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
		exchange.sendResponseHeaders(302, -1);
	}

	/**
	 * インタフェーステーマリソースリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void requestWebuiTheme(HttpExchange exchange) throws IOException {
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
		exchange.sendResponseHeaders(200, resourceData.length);

		/*
		 * レスポンス出力
		 */
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(resourceData);
		}
	}

	/**
	 * インタフェースリソースリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void requestWebui(HttpExchange exchange) throws IOException {
		/*
		 * リクエストパス取得
		 */
		String requestPath = Objects.toString(exchange.getRequestURI().getPath(), "");
		log.debug("インタフェースリソースリクエストを受け付けました(" + requestPath + ")。");

		/*
		 * プロジェクト未選択時のプロジェクトページリダイレクト
		 */
		if (!isProjectSelected() && !requestPath.endsWith(WEBUI_PROJECT_SETTING_PAGE) && requestPath.endsWith(".html")) {
			exchange.getResponseHeaders().set("Connection", "close");
			exchange.getResponseHeaders().set("Location", WEBUI_PROJECT_SETTING_PAGE);
			exchange.sendResponseHeaders(302, -1);
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
		exchange.sendResponseHeaders(200, resourceData.length);

		/*
		 * レスポンス出力
		 */
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(resourceData);
		}
	}

	/**
	 * SSE接続リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void sseConnect(HttpExchange exchange) throws IOException {
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
		exchange.sendResponseHeaders(200, 0);

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
		AgentWebUIEventController.instance().getEventBuffer().forEach(queue::offer);

		/*
		 * エージェントイベントキューイングオブジェクト登録
		 */
		Consumer<String> subscriber = queue::offer;
		AgentWebUIEventController.instance().subscribe(subscriber);

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
			// クライアント切断(正常終了として無視)
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			AgentWebUIEventController.instance().unsubscribe(subscriber);
			try {
				os.close();
			} catch (IOException e) {
				// ストリームクローズに関しての例外は無視
			}
		}
	}

	/**
	 * 現在選択中のプロジェクトのリーダーエージェント数を返却します。<br>
	 * @param projectName プロジェクト名
	 * @return リーダーエージェント数
	 */
	private int countLeaderAgents(String projectName) {
		Path agentsPath = config.getApplicationAgentsPath(projectName);
		if (!Files.isDirectory(agentsPath)) {
			return 0;
		}
		try (Stream<Path> stream = Files.list(agentsPath)) {
			return (int) stream //
					.filter(file -> file.getFileName().toString().endsWith(".properties")) //
					.filter(file -> {
						Properties properties = new Properties();
						try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
							properties.load(reader);
						} catch (IOException e) {
							return false;
						}
						return Boolean.parseBoolean(properties.getProperty("agent.leader", "false"));
					}) //
					.count();
		} catch (IOException e) {
			return 0;
		}
	}

	/**
	 * プロジェクト選択受付リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiSelectProject(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("オーケストレーター実行中はプロジェクトを変更できません。"));
			return;
		}
		try {
			context = new ContextFactory(config).create(projectName);
			AgentWebUIEventController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
		} catch (Throwable e) {
			log.error("コンテキストオブジェクトの生成に失敗しました。", e);
			sendApiResponse(exchange, AgentWebUIApiResponse.error("処理コンテキストの生成に失敗しました。"));
			return;
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();
		result.put("project", projectName);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * オーケストレーター処理開始リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiStartOrchestrator(HttpExchange exchange) throws IOException {
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
		int leaderCount = countLeaderAgents(context.getProjectName());
		if (prompt.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロンプトを入力してください。"));
			return;
		}
		if (!isProjectSelected() || isOrchestratorRunning()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("オーケストレーターの処理を開始できるステータスにありません。"));
			return;
		}
		if (leaderCount == 0) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("リーダーエージェントが設定されていないため処理を開始できません。"));
			return;
		}
		if (leaderCount > 1) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("リーダーエージェントが複数設定されているため処理を開始できません。"));
			return;
		}

		/*
		 * コンテキストタスク設定
		 */
		context.setTask(prompt);

		/*
		 * オーナー会話エントリ追加 + SSEイベント発行
		 */
		String ownerTimestamp = DEFAULT_DATE_FORMAT.format(new Date());
		context.getConversations().add(OWNER_AGENT_NAME, prompt, null);
		AgentWebUIEventController.instance().publishStart(OWNER_AGENT_NAME, null, ownerTimestamp);
		AgentWebUIEventController.instance().publishContent(OWNER_AGENT_NAME, prompt);
		AgentWebUIEventController.instance().publishFinish(OWNER_AGENT_NAME);

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
				AgentWebUIEventController.instance().publishOrchestratorStarted();
				orchestrator.execute();
			} catch (Throwable e) {
				log.error("オーケストレーター実行に失敗しました。", e);
			}
		});

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * オーケストレーター処理停止リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiStopOrchestrator(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("オーケストレーターの処理を停止できるステータスにありません。"));
			return;
		}

		/*
		 * 停止シグナル送出
		 */
		stopSignalFuture.complete(null);

		/*
		 * SSEイベント発行
		 */
		AgentWebUIEventController.instance().publishOrchestratorStopped();

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * オーケストレーターステータス取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetOrchestratorStatus(HttpExchange exchange) throws IOException {
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
		int leaderCount = isProjectSelected() ? countLeaderAgents(context.getProjectName()) : 0;
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * カレントプロジェクト取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetCurrentProject(HttpExchange exchange) throws IOException {
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
		if (!isProjectSelected()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();
		result.put("project", context.getProjectName());

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクト情報取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetProject(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト名が指定されていません。"));
			return;
		}
		if (!Files.isReadable(config.getApplicationProjectPropertiesPath(projectName))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト設定ファイルが読み込めません。"));
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

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクト情報保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiSaveProject(HttpExchange exchange) throws IOException {
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
		boolean isRename = !originalProject.isBlank() && !originalProject.equals(projectName);
		if (projectName.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト名を入力してください。"));
			return;
		}
		if (!projectName.matches("[a-zA-Z0-9]+")) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト名は半角英数字のみ使用できます。"));
			return;
		}
		if (projectName.length() > 32) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト名は32文字以内で入力してください。"));
			return;
		}
		if (isNew && Files.exists(config.getApplicationProjectPath(projectName))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("同名のプロジェクトが既に存在します。"));
			return;
		}
		if (isRename && !Files.exists(config.getApplicationProjectPath(originalProject))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("変更元のプロジェクトが見つかりません。"));
			return;
		}
		if (isRename && Files.exists(config.getApplicationProjectPath(projectName))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("同名のプロジェクトが既に存在します。"));
			return;
		}
		if (title.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトタイトルを入力してください。"));
			return;
		}
		if (title.length() > 80) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトタイトルは80文字以内で入力してください。"));
			return;
		}

		/*
		 * プロジェクトディレクトリ作成(リネーム時はディレクトリ移動)
		 */
		boolean isNewProject = !isRename && !Files.exists(config.getApplicationProjectPath(projectName));
		if (isRename) {
			Files.move(config.getApplicationProjectPath(originalProject), config.getApplicationProjectPath(projectName));
			if (isProjectSelected() && originalProject.equals(context.getProjectName())) {
				try {
					context = new ContextFactory(config).create(projectName);
					AgentWebUIEventController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
				} catch (Throwable e) {
					log.warn("リネーム後のコンテキスト再構築に失敗しました。", e);
					context = null;
				}
			}
			log.info("プロジェクトディレクトリをリネームしました({} -> {})。", originalProject, projectName);
		}
		Files.createDirectories(config.getApplicationProjectPath(projectName));
		Files.createDirectories(config.getApplicationLogPath(projectName));
		Files.createDirectories(config.getApplicationAgentsPath(projectName));

		/*
		 * 新規プロジェクト時はテンプレートリソースをプロジェクトディレクトリへ配置
		 * (複写時はテンプレート配置をスキップ)
		 */
		if (isNewProject && copyFromProject.isBlank()) {
			try {
				ResourceUtils.copyResourceDirectory(RESOURCE_TEMPLATE_PROJECT, config.getApplicationProjectPath(projectName));
				log.info("プロジェクトテンプレートを配置しました({})。", config.getApplicationProjectPath(projectName));
			} catch (Throwable e) {
				log.warn("プロジェクトテンプレートの配置に失敗しました。", e);
			}
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
			}
			if (templateAgentsResource != null) {
				try {
					ResourceUtils.copyResourceDirectory(templateAgentsResource, config.getApplicationAgentsPath(projectName));
					log.info("エージェントテンプレートを配置しました({} -> {})。", templateAgentsResource, config.getApplicationAgentsPath(projectName));
				} catch (Throwable e) {
					log.warn("エージェントテンプレートの配置に失敗しました。", e);
				}
			}
		}

		/*
		 * 複写時はコピー元プロジェクトをディレクトリツリーごとコピー
		 * (.orchestrator配下はagentsディレクトリのみコピー)
		 */
		if (isNewProject && !copyFromProject.isBlank()) {
			Path sourcePath = config.getApplicationProjectPath(copyFromProject);
			Path destPath = config.getApplicationProjectPath(projectName);
			Path sourceAgentsPath = config.getApplicationAgentsPath(copyFromProject);
			Path sourceOrchestratorPath = sourceAgentsPath.getParent();
			if (Files.isDirectory(sourcePath)) {
				try (Stream<Path> walk = Files.walk(sourcePath)) {
					for (Path src : walk.collect(Collectors.toList())) {
						// .orchestrator配下はagentsのみ(.orchestratorディレクトリ自体は通過)
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
				log.info("プロジェクトを複写しました({} -> {})。", copyFromProject, projectName);
			}
		}

		/*
		 * プロジェクトプロパティ保存
		 */
		ProjectConfig projectConfig = new ProjectConfig(config.getApplicationProjectPropertiesPath(projectName));
		projectConfig.setName(projectName);
		projectConfig.setTitle(title);
		projectConfig.save(config.getApplicationProjectPropertiesPath(projectName));

		/*
		 * レスポンスデータ生成
		 */
		Map<String, String> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result, "プロジェクトを登録しました。"));
	}

	/**
	 * プロジェクト一覧取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetProjectList(HttpExchange exchange) throws IOException {
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
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
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
								long first = sdf.parse(firstTimestamp).getTime();
								long last = sdf.parse(lastTimestamp).getTime();
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * エージェント一覧取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetAgentList(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトが選択されていません。。"));
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
					agent.put("filename", file.getFileName().toString());
					agent.put("name", agentName);
					agent.put("type", properties.getProperty("agent.type", "claude-cli"));
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * エージェント保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiSaveAgent(HttpExchange exchange) throws IOException {
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
		String type = request.path("type").asString("claude-cli");
		String model = request.path("model").asString("");
		String extraArgs = request.path("extraArgs").asString("");
		boolean leader = request.path("leader").asBoolean(false);
		String role = request.path("role").asString("");
		String personality = request.path("personality").asString("");
		String filename = name + ".properties";
		if (projectName.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}
		if (name.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("エージェント名を入力してください。"));
			return;
		}
		if (OWNER_AGENT_NAME.equalsIgnoreCase(name)) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("\"" + OWNER_AGENT_NAME + "\" はシステム予約名のため使用できません。"));
			return;
		}
		if (!name.matches("[a-zA-Z0-9]+")) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("エージェント名は半角英数字のみ使用できます。"));
			return;
		}
		if (name.length() > 32) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("エージェント名は32文字以内で入力してください。"));
			return;
		}
		if (model.length() > 32) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("モデルは32文字以内で入力してください。"));
			return;
		}
		if (extraArgs.length() > 128) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("追加引数は128文字以内で入力してください。"));
			return;
		}
		if (role.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("役割名を入力してください。"));
			return;
		}
		if (role.length() > 80) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("役割名は80文字以内で入力してください。"));
			return;
		}
		boolean isAgentRename = !originalName.isBlank() && !originalName.equals(name);
		if (isNewAgent && Files.exists(config.getApplicationAgentsPath(projectName).resolve(filename))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("同名のエージェントが既に存在します。"));
			return;
		}
		if (isAgentRename && Files.exists(config.getApplicationAgentsPath(projectName).resolve(filename))) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("同名のエージェントが既に存在します。"));
			return;
		}
		if (!isNewAgent && isProjectSelected() && projectName.equals(context.getProjectName())) {
			String lookupName = originalName.isBlank() ? name : originalName;
			final String finalLookup = lookupName;
			boolean inConversation = context.getConversations().getAll().stream().anyMatch(c -> finalLookup.equals(c.getAgentName()));
			if (inConversation) {
				sendApiResponse(exchange, AgentWebUIApiResponse.error("既に会話に参加しているため、変更することはできません。"));
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
					if (!type.equals(originalProps.getProperty("agent.type", "claude-cli"))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!model.equals(Objects.toString(originalProps.getProperty("agent.model"), ""))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!extraArgs.equals(Objects.toString(originalProps.getProperty("agent.extra.args"), ""))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (leader != Boolean.parseBoolean(originalProps.getProperty("agent.leader", "false"))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!role.equals(Objects.toString(originalProps.getProperty("agent.role"), ""))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
					if (!personality.equals(Objects.toString(originalProps.getProperty("agent.personality"), ""))) {
						sendApiResponse(exchange, AgentWebUIApiResponse.error("既にセッションが存在するため変更できません。セッションを削除してから変更してください。"));
						return;
					}
				}
			}
		}

		/*
		 * エージェントプロパティ保存
		 */
		Path agentsPath = config.getApplicationAgentsPath(projectName);
		Files.createDirectories(agentsPath);
		AgentConfig agentConfig = new AgentConfig();
		agentConfig.setName(name);
		agentConfig.setType(type);
		agentConfig.setModel(model);
		agentConfig.setLeader(leader);
		agentConfig.setRole(role);
		agentConfig.setPersonality(personality);
		agentConfig.setExtraArgs(List.of(extraArgs.split("\\s+")));
		agentConfig.save(agentsPath.resolve(filename));

		/*
		 * リネーム時は旧プロパティファイルを削除、セッションエントリのキーを更新
		 */
		if (isAgentRename) {
			Files.deleteIfExists(agentsPath.resolve(originalName + ".properties"));
			if (isProjectSelected() && projectName.equals(context.getProjectName())) {
				context.getSessions().rename(originalName, name);
			}
		}

		/*
		 * コンテキスト再構築(SSEバッファのエージェントリスト更新)
		 */
		if (isProjectSelected() && projectName.equals(context.getProjectName()) && !isOrchestratorRunning()) {
			try {
				context = new ContextFactory(config).create(projectName);
				AgentWebUIEventController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
			} catch (Throwable e) {
				log.warn("エージェント保存後のコンテキスト再構築に失敗しました。", e);
			}
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("filename", filename);

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * エージェント削除APIリクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiDeleteAgent(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトが選択されていません。"));
			return;
		}
		if (name.isBlank()) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("エージェント名が指定されていません。"));
			return;
		}
		if (isProjectSelected() && projectName.equals(context.getProjectName())) {
			final String finalName = name;
			boolean inConversation = context.getConversations().getAll().stream().anyMatch(c -> finalName.equals(c.getAgentName()));
			if (inConversation) {
				sendApiResponse(exchange, AgentWebUIApiResponse.error("既に会話に参加しているため、変更することはできません。"));
				return;
			}
		}

		/*
		 * エージェントプロパティ削除
		 */
		Path agentsPath = config.getApplicationAgentsPath(projectName);
		Files.deleteIfExists(agentsPath.resolve(filename));

		/*
		 * コンテキスト再構築(SSEバッファのエージェントリスト更新)
		 */
		if (isProjectSelected() && projectName.equals(context.getProjectName()) && !isOrchestratorRunning()) {
			try {
				context = new ContextFactory(config).create(projectName);
				AgentWebUIEventController.instance().preloadBuffer(new ArrayList<>(context.getAgents().values()), context.getConversations(), context.getSessions());
			} catch (Throwable e) {
				log.warn("エージェント削除後のコンテキスト再構築に失敗しました。", e);
			}
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクト削除リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiDeleteProject(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクト名が指定されていません。"));
			return;
		}

		/*
		 * プロジェクトディレクトリ削除
		 */
		Path projectPath = config.getApplicationProjectPath(projectName);
		if (Files.exists(projectPath)) {
			try (Stream<Path> walk = Files.walk(projectPath)) {
				walk.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}

		/*
		 * 削除対象がアクティブプロジェクトの場合はコンテキストをクリア
		 */
		if (isProjectSelected() && projectName.equals(context.getProjectName())) {
			context = null;
			orchestratorExecuteFuture = null;
			stopSignalFuture = null;
			log.info("アクティブプロジェクトが削除されたためコンテキストをクリアしました({})。", projectName);
		}

		/*
		 * レスポンスデータ生成
		 */
		Map<String, Object> result = new LinkedHashMap<>();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * 環境設定取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetConfig(HttpExchange exchange) throws IOException {
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * 環境設定保存リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiSaveConfig(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("リポジトリパスを入力してください。"));
			return;
		}
		try {
			int port = Integer.parseInt(webuiPort);
			if (port < 1 || port > 65535) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("WebUIポートには1～65535の整数を入力してください。"));
			return;
		}
		try {
			if (Integer.parseInt(webuiConnection) < 1) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("WebUI同時接続数には1以上の整数を入力してください。"));
			return;
		}
		try {
			if (Integer.parseInt(agentTimeout) < 1) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			sendApiResponse(exchange, AgentWebUIApiResponse.error("エージェントタイムアウトには1以上の整数を入力してください。"));
			return;
		}

		/*
		 * Configセッターで値を更新してファイルに保存
		 */
		config.setApplicationTheme(request.path("theme").asString("").trim());
		config.setApplicationRepositoryPath(repositoryPath);
		config.setApplicationWebuiPort(Integer.parseInt(webuiPort));
		config.setApplicationWebuiConnection(Integer.parseInt(webuiConnection));
		config.setAgentTimeout(Integer.parseInt(agentTimeout));
		config.setAgentCliCommand(AgentType.CLAUDE_CLI.getName(), request.path("cliClaudeCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.GEMINI_CLI.getName(), request.path("cliGeminiCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.CODEX_CLI.getName(), request.path("cliCodexCommand").asString("").trim());
		config.setAgentCliCommand(AgentType.COPILOT_CLI.getName(), request.path("cliCopilotCommand").asString("").trim());
		config.save();

		/*
		 * レスポンス返却
		 */
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(null, "環境設定を保存しました。ポートや接続数の変更はサーバー再起動後に反映されます。"));
	}

	/**
	 * 会話ログ取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetConversationLog(HttpExchange exchange) throws IOException {
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
			sendApiResponse(exchange, AgentWebUIApiResponse.error("プロジェクトが選択されていません。"));
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
					item.put("content", removeIstructLine(entry.path("content").asString("")));
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

	/**
	 * プロジェクトデフォルト値取得リクエスト時の処理を行います。<br>
	 * @param exchange リクエストレスポンス情報
	 * @throws IOException リクエスト処理に失敗した場合にスローされます
	 */
	private void apiGetDefaultProject(HttpExchange exchange) throws IOException {
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
		try (Reader reader = new InputStreamReader(new BufferedInputStream(AgentWebUIServer.class.getResourceAsStream(DEFAULT_PROJECT_FILE)), StandardCharsets.UTF_8)) {
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
		sendApiResponse(exchange, AgentWebUIApiResponse.ok(result));
	}

}
