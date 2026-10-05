package vn.roboworld.hcc

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * TRỢ LÝ NGOÀI — kho tri thức CHÍNH của app.
 *
 * Gọi API của thutuc.hanhchinhso.ai.vn (đơn vị khách đang dùng sẵn, đã nạp bộ thủ tục).
 * Anh Trường chốt 18/09/2026: đây là kho chính; kho 165 thủ tục nằm trong robot lùi
 * xuống làm lưới đỡ khi trợ lý ngoài không trả lời được.
 *
 * Giao thức, đo trên máy chủ thật 18/09/2026:
 *
 *     POST /api/chat/stream
 *     Content-Type: application/json
 *     {"query":"...","language":"vi","sessionId":"..."}   ← trường là "query", KHÔNG phải "message"
 *
 *     event: thinking       {"status":"Analyzing your request..."}
 *     event: claude_delta   {"text":"..."}        ← chữ chảy dần, CÓ THỂ là thông báo lỗi
 *     event: answer         {"answer":"...","citations":[],"intent":"...","sessionId":"..."}
 *     event: done           {}
 *
 * ⚠ BỐN ĐIỀU ĐÃ ĐO, đừng dò lại:
 *
 *  ① Trường phải là `query`. Gửi `message` thì máy chủ trả:
 *       {"error":"Field \"query\" is required and must be a non-empty string"}
 *
 *  ② KHÔNG cần token, KHÔNG cần cookie. Máy chủ có CORS chỉ mở cho tên miền của họ,
 *    nhưng CORS là luật của TRÌNH DUYỆT — app Android không bị ràng buộc.
 *
 *  ③ Lỗi hạn mức của họ rơi thẳng vào trường `answer`:
 *       {"answer":"⚠️ Lưu ý kiểm chứng… You've hit your limit · resets 1pm…"}
 *    Nghĩa là KHÔNG THỂ chỉ nhìn mã HTTP để biết câu có dùng được hay không — HTTP
 *    vẫn 201. Phải soi nội dung. Việc đó do locTraLoiNgoai() bên lớp web làm, và lớp
 *    này chặn thêm một lần nữa cho chắc: đây là thứ tuyệt đối không được ra loa.
 *
 *  ④ Phải đặt User-Agent tường minh — xem Cai.USER_AGENT.
 *
 * Lớp này CHỈ lấy chữ về. Việc bóc markup, chặn câu hỏng, quyết định đọc hay không
 * nằm ở locTraLoiNgoai() trong khung-app.html — một chỗ duy nhất, có bộ thử riêng
 * (tools/thu-tro-ly-ngoai.mjs).
 */
object TroLyNgoai {

    private const val TAG = "HccNgoai"

    /** Một luồng riêng: gọi mạng không bao giờ được chạy trên luồng giao diện. */
    private val tho = Executors.newSingleThreadExecutor()

    /** Máy chủ của họ có ý niệm phiên. Giữ lại để câu sau nối được câu trước. */
    @Volatile
    private var phien: String? = null

    /** Lý do lần gọi gần nhất hỏng — hiện trong bảng tự chẩn đoán. */
    @Volatile
    var loiCuoi: String = ""
        private set

    /** Căn cứ pháp lý của câu trả lời gần nhất, dạng JSON. Rỗng là "[]". */
    @Volatile
    var canCuCuoi: String = "[]"
        private set

    /** Lần gần nhất trợ lý ngoài trả lời được — để biết nó còn sống hay không. */
    @Volatile
    var lucTraLoiCuoi: Long = 0
        private set

    fun xoaPhien() {
        phien = null
        Log.d(TAG, "Đã xoá phiên trợ lý ngoài")
    }

    /**
     * Hỏi trợ lý ngoài.
     *
     * @param khiXong nhận (câu trả lời THÔ, lý do hỏng). Đúng một trong hai khác null.
     *                Luôn được gọi đúng MỘT lần, kể cả khi hỏng — nếu không thì tầng
     *                trên treo mãi và robot đứng câm.
     */
    fun hoi(cauHoi: String, khiXong: (String?, String?) -> Unit) {
        if (!Cai.BAT_TRO_LY_NGOAI) {
            khiXong(null, "tầng trợ lý ngoài đang tắt")
            return
        }
        val q = cauHoi.trim()
        if (q.isEmpty()) {
            khiXong(null, "câu hỏi rỗng")
            return
        }
        tho.execute {
            canCuCuoi = "[]"          // câu mới thì căn cứ cũ phải bỏ, không thì lệch nguồn
            val batDau = System.currentTimeMillis()
            try {
                val tra = goiMotLan(q)
                val ms = System.currentTimeMillis() - batDau
                if (tra.isNullOrBlank()) {
                    loiCuoi = "máy chủ không trả về câu nào"
                    Log.w(TAG, "Trợ lý ngoài im (${ms}ms)")
                    khiXong(null, loiCuoi)
                } else {
                    lucTraLoiCuoi = System.currentTimeMillis()
                    loiCuoi = ""
                    Log.d(TAG, "Trợ lý ngoài trả lời sau ${ms}ms, ${tra.length} ký tự")
                    khiXong(tra, null)
                }
            } catch (e: Exception) {
                val ms = System.currentTimeMillis() - batDau
                loiCuoi = (e.message ?: e.javaClass.simpleName)
                Log.w(TAG, "Trợ lý ngoài hỏng sau ${ms}ms: $loiCuoi")
                khiXong(null, loiCuoi)
            }
        }
    }

    /** Gọi một lần, đọc luồng SSE, trả về chuỗi trong trường `answer`. */
    private fun goiMotLan(q: String): String? {
        val than = JSONObject().apply {
            put("query", q)                 // ⚠ "query", không phải "message"
            put("language", "vi")
            phien?.let { put("sessionId", it) }
        }.toString()

        val noi = (URL(Cai.URL_TRO_LY_NGOAI).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Cai.HAN_NOI_MS
            readTimeout = Cai.HAN_CHO_NGOAI_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", Cai.USER_AGENT)
        }

        try {
            noi.outputStream.use { it.write(than.toByteArray(Charsets.UTF_8)) }

            val ma = noi.responseCode
            if (ma !in 200..299) {
                throw RuntimeException("máy chủ trả mã $ma")
            }

            /* Đọc SSE. Mỗi sự kiện là một khối "event: <tên>" + "data: <json>".
               Ta chỉ cần khối answer; thinking và claude_delta bỏ qua —
               ⚠ claude_delta CHÍNH LÀ chỗ thông báo hết hạn mức hiện ra, nhưng nội
               dung đó cũng được lặp lại trong answer nên không cần đọc riêng. */
            var traLoi: String? = null
            BufferedReader(InputStreamReader(noi.inputStream, Charsets.UTF_8)).use { doc ->
                var ten = ""
                while (true) {
                    val dong = doc.readLine() ?: break
                    when {
                        dong.startsWith("event:") -> ten = dong.removePrefix("event:").trim()
                        dong.startsWith("data:") -> {
                            val d = dong.removePrefix("data:").trim()
                            if (ten == "answer" && d.isNotEmpty()) {
                                val o = runCatching { JSONObject(d) }.getOrNull()
                                if (o != null) {
                                    o.optString("sessionId").takeIf { it.isNotBlank() }
                                        ?.let { phien = it }
                                    traLoi = o.optString("answer").takeIf { it.isNotBlank() }
                                    /* Căn cứ pháp lý nguồn trả kèm — số hiệu văn bản,
                                       điều khoản, số trang. Giữ nguyên dạng JSON, lớp
                                       web tự bày; app không diễn giải lại một chữ. */
                                    canCuCuoi = o.optJSONArray("citations")?.toString() ?: "[]"
                                }
                            } else if (ten == "error" && d.isNotEmpty()) {
                                val o = runCatching { JSONObject(d) }.getOrNull()
                                throw RuntimeException(
                                    o?.optString("error")?.takeIf { it.isNotBlank() }
                                        ?: "máy chủ báo lỗi")
                            }
                        }
                        dong.isBlank() -> ten = ""
                    }
                }
            }
            return traLoi
        } finally {
            runCatching { noi.disconnect() }
        }
    }

    /**
     * Hàng rào THỨ HAI, đặt ở tầng Kotlin.
     *
     * Bộ lọc chính nằm bên lớp web (locTraLoiNgoai). Lớp này kiểm lại đúng một thứ:
     * câu báo hết hạn mức bằng tiếng Anh. Lý do làm hai lần cho một việc — nếu WebView
     * chưa nạp xong, hoặc ai đó sau này gọi thẳng TroLyNgoai mà quên qua bộ lọc, thì
     * hàng rào này vẫn còn. Robot đứng giữa sảnh đọc "You've hit your limit" là sự cố
     * không được phép xảy ra dù chỉ một lần.
     */
    private val DAU_HIEU_HONG = Regex(
        "hit your limit|rate limit|quota|resets?\\s+\\d{1,2}\\s*(am|pm)|" +
        "resets?\\s+\\w{3}\\s+\\d{1,2}|" +                       // "resets Sep 24, 8am"
        "internal server error|bad gateway|something went wrong|" +
        // Chính họ ghi dòng này khi câu trả lời KHÔNG dựa trên kho văn bản.
        // Đo 18/09/2026: đó là đường hỏi mô hình ngôn ngữ, không có căn cứ pháp lý.
        "lưu ý kiểm chứng|chưa trích dẫn được căn cứ pháp lý",
        RegexOption.IGNORE_CASE)

    fun coDauHieuHong(cau: String?): Boolean =
        !cau.isNullOrBlank() && DAU_HIEU_HONG.containsMatchIn(cau)
}
