package org.ideaccum.ai.orchestrator.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.webui.WebUIController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.node.StringNode;

/**
 * エージェントの出力関連のユーティリティクラスです。<br>
 * <p>
 * エージェントのレスポンスや発言内容をダンプ出力する機能を提供します。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/19	Kitagawa		新規作成
 *-->
 */
public class OutputUtils implements Constants {

	/**
	 * コンストラクタ<br>
	 */
	private OutputUtils() {
		super();
	}

	/**
	 * コンテンツ内容がJSONであるか判定します。<br>
	 * @param content コンテンツ内容
	 * @return JSONである場合にtrueを返却
	 */
	private static boolean isJSON(String content) {
		try {
			JSON.readTree(content);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * エージェントレスポンスダンプを出力します。<br>
	 * @param context コンテキストオブジェクト
	 * @param agentName エージェント名
	 * @param response レスポンス内容
	 */
	public static void outputResponseDump(Context context, String agentName, String response) {
		try {
			String outputFilename = "respnse-dump-" + agentName + ".json";
			Path outputPath = context.getConfig().getApplicationLogPath(context.getProjectName());
			Path outputFile = Paths.get(context.getConfig().getApplicationLogPath(context.getProjectName()).toString(), outputFilename);

			List<DumpEntry> outputList = new LinkedList<>();
			if (Files.exists(outputFile)) {
				try {
					outputList = JSON.readValue(outputFile.toFile(), JSON.getTypeFactory().constructCollectionType(List.class, DumpEntry.class));
				} catch (Throwable e) {
				}
			}

			Object outputContent = isJSON(response) ? JSON.readTree(response) : new StringNode(response).toPrettyString();
			DumpEntry outputObject = new DumpEntry(DATE_FORMAT_YYYY_MM_DD_HH_MM_SS.format(new Date()), outputContent);
			outputList.add(outputObject);

			if (!Files.exists(outputPath)) {
				Files.createDirectories(outputPath);
			}

			JSON.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), outputList);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * エージェント発言ダンプを出力します。<br>
	 * @param context コンテキストオブジェクト
	 * @param agentName エージェント名
	 * @param content 発言内容
	 */
	public static void outputConversationDump(Context context, String agentName, String content) {
		try {
			String outputFilename = "conversation-dump-" + agentName + ".json";
			Path outputPath = context.getConfig().getApplicationLogPath(context.getProjectName());
			Path outputFile = Paths.get(context.getConfig().getApplicationLogPath(context.getProjectName()).toString(), outputFilename);

			List<DumpEntry> outputList = new LinkedList<>();
			if (Files.exists(outputFile)) {
				outputList = JSON.readValue(outputFile.toFile(), JSON.getTypeFactory().constructCollectionType(List.class, DumpEntry.class));
			}

			Object outputContent = new StringNode(content).toPrettyString();
			DumpEntry outputObject = new DumpEntry(DATE_FORMAT_YYYY_MM_DD_HH_MM_SS.format(new Date()), outputContent);
			outputList.add(outputObject);

			if (!Files.exists(outputPath)) {
				Files.createDirectories(outputPath);
			}

			WebUIController.instance().publishContent(agentName, content);
			JSON.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), outputList);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * ダンプレコードエントリクラスです。<br>
	 * @param timestamp レコードタイムスタンプ
	 * @param response レスポンス内容
	 *
	 * @author Kitagawa<br>
	 *
	 *<!--
	 * 更新日		更新者			更新内容
	 * 2026/04/01	Kitagawa		新規作成
	 *-->
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DumpEntry(String timestamp, Object response) {
	}
}
