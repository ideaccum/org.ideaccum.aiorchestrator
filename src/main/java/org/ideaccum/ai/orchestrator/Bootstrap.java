package org.ideaccum.ai.orchestrator;

import java.nio.file.Path;

import org.ideaccum.ai.orchestrator.context.Config;
import org.ideaccum.ai.orchestrator.webui.AgentWebUIServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * アプリケーションメインクラスです。<br>
 * <p>
 * このクラスは、アプリケーションのエントリーポイントとして機能します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public final class Bootstrap implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

	/**
	 * コンストラクタ<br>
	 */
	public Bootstrap() {
		super();
	}

	/**
	 * メインメソッドです。<br>
	 * @param args コマンドライン引数
	 * @throws Throwable 処理実行時に予期せぬエラーが発生した場合にスローされます
	 */
	public static void main(String[] args) throws Throwable {
		try {
			/*
			 * 環境設定情報読み込み(ファイルが存在しない場合はデフォルト設定から自動生成)
			 */
			String configFile = args != null && args.length >= 1 ? args[0] : CONFIG_FILE;
			Config config = new Config(Path.of(configFile));

			/*
			 * Webインタフェースサーバー起動
			 */
			AgentWebUIServer webuiServer = new AgentWebUIServer(config);
			Runtime.getRuntime().addShutdownHook(new Thread(webuiServer::destroy));
			webuiServer.start();
			log.info("Webインタフェースサーバーを起動しました。");

			/*
			 * 実質永久ループ
			 */
			Thread.sleep(Long.MAX_VALUE);
		} catch (Throwable e) {
			log.error("予期せぬエラーが発生したため、アプリケーションを終了します。", e);
			System.exit(1);
		} finally {
			log.info("アプリケーションを終了します。");
			System.exit(0);
		}
	}
}
