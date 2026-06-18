/**
 * アプリケーションユーティリティクラスです。<br>
 * <p>
 * 汎用的な処理メソッドを提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
class Utils {
	/** 制御キーワード情報キャッシュ(null=未取得) */
	static #controlKeywords = null;

	/**
	 * アプリケーション共通の初期化処理を実行します。<br>
	 * 各画面の Promise.all に並べて呼び出してください。<br>
	 */
	static async init() {
		await Utils.loadControlKeywords();
	}

	/**
	 * 指定名称から一貫したアバター背景色を返します。<br>
	 * @param {string} name - 指定名称
	 * @returns {string} CSS色文字列
	 */
	static avatarColor(name) {
		const palette = [
			"#1a73e8", // ブルー
			"#0f9d58", // グリーン
			"#f4511e", // ディープオレンジ
			"#9c27b0", // パープル
			"#00897b", // ティール
			"#e53935", // レッド
			"#0288d1", // ライトブルー
			"#f09300", // アンバー
			"#546e7a", // ブルーグレー
			"#6d4c41", // ブラウン
			"#43a047", // ライトグリーン
			"#d81b60", // ピンク
			"#3949ab", // インディゴ
			"#00acc1", // シアン
			"#7cb342", // ライム
			"#8e24aa", // ディープパープル
			"#fb8c00", // オレンジ
			"#039be5", // スカイブルー
			"#c0392b", // ダークレッド
			"#00796b", // ダークティール
		];
		let hash = 0;
		for (let i = 0; i < name.length; i++) {
			hash = (hash * 31 + name.charCodeAt(i)) & 0xffffffff;
		}
		return palette[Math.abs(hash) % palette.length];
	}

	/**
	 * 文字列をHTMLエスケープして提供します。<br>
	 * @param {string} string - エスケープ対象文字列
	 * @returns {string} エスケープ済み文字列
	 */
	static esc(string) {
		return (string || "") //
			.replace(/&/g, "&amp;") //
			.replace(/</g, "&lt;") //
			.replace(/>/g, "&gt;") //
			.replace(/"/g, "&quot;"); //
	}

	/**
	 * 秒数を "h時間m分s秒" 形式の文字列に変換します。<br>
	 * 時がなければ "m分s秒"、時分がなければ "s秒" のみ表示します。
	 * @param {number} sec - 秒数
	 * @returns {string}
	 */
	static formatDurationSec(sec) {
		if (!sec || sec <= 0) {
			return "0秒";
		}
		const h = Math.floor(sec / 3600);
		const m = Math.floor((sec % 3600) / 60);
		const s = sec % 60;
		if (h > 0) {
			return `${h}時間${m}分${s}秒`;
		}
		if (m > 0) {
			return `${m}分${s}秒`;
		}
		return `${s}秒`;
	}

	/**
	 * ミリ秒を "h時間m分s秒" 形式の文字列に変換します。<br>
	 * @param {number} ms - ミリ秒
	 * @returns {string}
	 */
	static formatDurationMs(ms) {
		return Utils.formatDurationSec(Math.floor((ms || 0) / 1000));
	}

	/**
	 * 制御キーワード情報をサーバーから取得してキャッシュします。<br>
	 * 取得済みの場合はキャッシュをそのまま返します。<br>
	 * @returns {Promise<object|null>} 制御キーワード情報
	 */
	static async loadControlKeywords() {
		if (Utils.#controlKeywords !== null) {
			return Utils.#controlKeywords;
		}
		const result = await WebAPI.getControlKeywords({}, false);
		if (result?.data) {
			const data = result.data;
			Utils.#controlKeywords = {
				finalizeKeyword: data.finalizeKeyword || "",
				interruptKeyword: data.interruptKeyword || "",
				dispatchRegexp: data.dispatchRegexp ? new RegExp(data.dispatchRegexp) : null,
			};
		}
		return Utils.#controlKeywords;
	}

	/**
	 * エージェント名・トークン情報からフッター表示用テキストを生成します。<br>
	 * オーナーエージェントの場合は "User Prompt" を返します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {number|null} inputTokens - 入力トークン数
	 * @param {number|null} outputTokens - 出力トークン数
	 * @returns {string} トークン表示テキスト
	 */
	static formatTokenText(agentName, inputTokens, outputTokens) {
		if (agentName === Constants.OWNER_AGENT_NAME) {
			return "User Prompt";
		}
		const inT = Number(inputTokens || 0);
		const outT = Number(outputTokens || 0);
		const total = inT + outT;
		if (total <= 0) {
			return "";
		}
		return total.toLocaleString() + "トークン (IN:" + inT.toLocaleString() + " / OUT:" + outT.toLocaleString() + ")";
	}

	/**
	 * 2つの "yyyy/MM/dd HH:mm:ss" 形式タイムスタンプから所要時間文字列を計算します。<br>
	 * @param {string|null} prevTimestamp - 前ターンのタイムスタンプ
	 * @param {string|null} curTimestamp - 現ターンのタイムスタンプ
	 * @returns {string|null} 所要時間文字列(計算不能時はnull)
	 */
	static calcElapsed(prevTimestamp, curTimestamp) {
		if (!prevTimestamp || !curTimestamp) {
			return null;
		}
		const parse = (ts) => {
			const m = ts.match(/(\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
			if (!m) {
				return null;
			}
			return new Date(parseInt(m[1]), parseInt(m[2]) - 1, parseInt(m[3]), parseInt(m[4]), parseInt(m[5]), parseInt(m[6]));
		};
		const prev = parse(prevTimestamp);
		const cur = parse(curTimestamp);
		if (!prev || !cur) {
			return null;
		}
		const diffMs = cur - prev;
		if (diffMs < 0) {
			return null;
		}
		return Utils.formatDurationSec(Math.floor(diffMs / 1000));
	}

	/**
	 * テキストから制御キーワード行（ディスパッチ・完了・中断）を除外します。<br>
	 * 累積テキスト全体に対してフィルタするため、チャンク分割されたキーワードにも対応します。<br>
	 * loadControlKeywords() 未実行時はフィルタせずそのまま返します。<br>
	 * @param {string} raw - フィルタ対象テキスト
	 * @returns {string} キーワード行を除いたテキスト
	 */
	static filterKeywordLines(raw) {
		if (!raw || !Utils.#controlKeywords) {
			return raw;
		}
		const { finalizeKeyword, interruptKeyword, dispatchRegexp } = Utils.#controlKeywords;
		return raw
			.split("\n")
			.filter((line) => {
				const trimmed = line.trim();
				if (finalizeKeyword && trimmed === finalizeKeyword) return false;
				if (interruptKeyword && trimmed === interruptKeyword) return false;
				if (dispatchRegexp && dispatchRegexp.test(trimmed)) return false;
				return true;
			})
			.join("\n");
	}
}
