package vn.roboworld.hcc

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * TRA CỨU TRÊN MẠNG cho câu hỏi ĐỜI THƯỜNG ngoài kiến thức đã nạp.
 *
 * Anh Trường yêu cầu 22/09/2026: câu hỏi ngoài kiến thức đã nạp thì robot tra trên
 * Google — trừ các câu thuộc phạm trù CHÍNH TRỊ, THỦ TỤC, PHÁP LUẬT.
 * Việc phân loại câu nằm ở MainApplication.nenTraMang() — chặn bằng MÃ, không nhờ
 * mô hình tự giác.
 *
 * ── HAI NHÀ, nhận theo tiền tố khoá ──
 *   AIza… → Gemini + công cụ google_search (tra Google thật, trả kèm nguồn)
 *   gsk_… → Groq, dòng groq/compound-mini (tự tra web)
 *
 * ── LUẬT SỐ MỘT: KHÔNG CÓ NGUỒN THÌ KHÔNG NÓI ──
 * Đo 22/09/2026 bằng khoá Groq: hỏi "quanh Tây Hòa có điểm du lịch nào" thì nó trả lời
 * "thác Dray Nur, thác Dray Sap và đồi chè Cầu Đất" — hai thác ở gần Buôn Ma Thuột, Cầu
 * Đất ở tận Lâm Đồng — và dấu vết cho thấy nó KHÔNG hề tra web, tự bịa. Nên câu trả lời
 * nào không kèm bằng chứng đã tra (groundingMetadata của Gemini, executed_tools của
 * Groq) là VỨT, dù đọc lên nghe hợp lý tới đâu.
 *
 * ── KHOÁ KHÔNG NẰM TRONG MÃ, KHÔNG NẰM TRONG APK ──
 * Đọc từ thẻ nhớ: files/cau-hinh/tra-mang.txt
 *   dòng 1: khoá · dòng 2 (tuỳ chọn): tên model · dòng bắt đầu bằng # là ghi chú
 * Không có file đó thì cả tầng này TẮT, app chạy y như trước.
 * Đẩy khoá lên robot: python tools/day-web.py --khoa <đường dẫn file khoá>
 *
 * ⚠ Ba bẫy đã dính (08-research/13-tro-ly-mang-groq-gemini): thiếu User-Agent là Groq
 *   trả 403 câm lặng · đừng dùng tên model bí danh "-latest" · gói miễn phí Groq trả 413
 *   khi kết quả tìm kiếm quá dài (đo 22/09/2026, 3/4 câu).
 */
object TraMang {

    private const val TAG = "TraMang"
    private val tho = Executors.newSingleThreadExecutor()

    /** Kết quả một lượt tra. [nguon] rỗng nghĩa là KHÔNG được đọc. */
    data class KetQua(val chu: String, val nguon: List<Pair<String, String>>)

    private data class Khoa(val nha: String, val khoa: String, val model: String)

    /** Có khoá hợp lệ trên thẻ nhớ không — không có thì tầng này tắt hẳn. */
    fun coKhoa(): Boolean = docKhoa() != null

    private fun docKhoa(): Khoa? {
        val ctx = MainApplication.boiCanh ?: return null
        val f = File(ctx.getExternalFilesDir(null), "cau-hinh/tra-mang.txt")
        if (!f.isFile) return null
        val dong = runCatching { f.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
        val co = dong.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val k = co.getOrNull(0) ?: return null
        val md = co.getOrNull(1)
        return when {
            k.startsWith("AIza") -> Khoa("gemini", k, md ?: Cai.MODEL_TRA_MANG_GEMINI)
            k.startsWith("gsk_") -> Khoa("groq", k, md ?: Cai.MODEL_TRA_MANG_GROQ)
            else -> { Log.w(TAG, "Khoá tra mạng không rõ nhà (tiền tố lạ) — tắt tầng"); null }
        }
    }

    /**
     * Lời dặn gửi mô hình. Dặn chỉ là lớp PHỤ — lớp chính là phân loại câu bằng mã
     * TRƯỚC khi gọi, và kiểm câu trả lời bằng mã SAU khi về.
     *
     * Nêu rõ địa giới vì đúng chỗ đó mô hình đã sai: xã Tây Hòa nay thuộc Đắk Lắk
     * nhưng trước 01/07/2025 là vùng Phú Yên cũ, gần Tuy Hòa, sát biển — không phải
     * vùng cao nguyên quanh Buôn Ma Thuột.
     */
    private const val LOI_DAN =
        "Bạn là robot hướng dẫn đặt tại Trung tâm Phục vụ Hành chính công xã Tây Hòa, " +
        "tỉnh Đắk Lắk. Lưu ý địa giới: từ 01/07/2025 xã Tây Hòa thuộc Đắk Lắk, trước đó " +
        "thuộc tỉnh Phú Yên (vùng duyên hải, gần Tuy Hòa). " +
        "Hãy TRA CỨU TRÊN MẠNG rồi trả lời bằng tiếng Việt, NGẮN GỌN tối đa ba câu, " +
        "giọng lịch sự, không dùng Markdown, không liệt kê đường link. " +
        "Chỉ nói điều tìm thấy trong kết quả tra cứu; không tìm thấy thì nói thẳng là " +
        "chưa tìm được thông tin. Không bàn chính trị, không hướng dẫn thủ tục hành chính " +
        "hay pháp luật."

    /** Gọi trên luồng riêng. [khiXong] nhận null + lý do nếu không dùng được. */
    fun hoi(cau: String, khiXong: (KetQua?, String?) -> Unit) {
        val k = docKhoa()
        if (k == null) { khiXong(null, "chưa có khoá"); return }
        tho.execute {
            val kq = runCatching {
                if (k.nha == "gemini") goiGemini(k, cau) else goiGroq(k, cau)
            }
            kq.onSuccess { (r, loi) -> khiXong(r, loi) }
              .onFailure { khiXong(null, "lỗi mạng: ${it.message?.take(80)}") }
        }
    }

    private fun post(url: String, than: JSONObject, them: Map<String, String>): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Cai.HAN_NOI_MS
            readTimeout = Cai.HAN_CHO_TRA_MANG_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", Cai.USER_AGENT)   // thiếu là 403 câm lặng
            them.forEach { (a, b) -> setRequestProperty(a, b) }
        }
        c.outputStream.use { it.write(than.toString().toByteArray(Charsets.UTF_8)) }
        val ma = c.responseCode
        val s = (if (ma in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return ma to s
    }

    private fun goiGemini(k: Khoa, cau: String): Pair<KetQua?, String?> {
        val than = JSONObject()
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", LOI_DAN))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", cau)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.3).put("maxOutputTokens", 400))
        val (ma, s) = post(
            "https://generativelanguage.googleapis.com/v1beta/models/${k.model}:generateContent?key=${k.khoa}",
            than, emptyMap())
        if (ma !in 200..299) return null to "Gemini HTTP $ma: ${s.take(120)}"

        val c = JSONObject(s).optJSONArray("candidates")?.optJSONObject(0)
            ?: return null to "Gemini không có candidates"
        // chữ có thể nằm ở NHIỀU part — phải nối lại
        val parts = c.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val chu = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text") ?: "" }

        val nguon = mutableListOf<Pair<String, String>>()
        val g = c.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
        for (i in 0 until g.length()) {
            val w = g.optJSONObject(i)?.optJSONObject("web") ?: continue
            nguon += (w.optString("title").ifBlank { "trang web" }) to w.optString("uri")
        }
        return KetQua(chu, nguon) to null
    }

    private fun goiGroq(k: Khoa, cau: String): Pair<KetQua?, String?> {
        val than = JSONObject()
            .put("model", k.model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", LOI_DAN))
                .put(JSONObject().put("role", "user").put("content", cau)))
            .put("temperature", 0.3).put("max_tokens", 400)
        val (ma, s) = post("https://api.groq.com/openai/v1/chat/completions", than,
                           mapOf("Authorization" to "Bearer ${k.khoa}"))
        if (ma !in 200..299) return null to "Groq HTTP $ma: ${s.take(120)}"

        val msg = JSONObject(s).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: return null to "Groq không có choices"
        val chu = msg.optString("content")
        // Bằng chứng đã tra web: executed_tools. Không có là mô hình tự nói — vứt.
        val cc = msg.optJSONArray("executed_tools") ?: JSONArray()
        val nguon = mutableListOf<Pair<String, String>>()
        for (i in 0 until cc.length()) {
            val t = cc.optJSONObject(i) ?: continue
            val kq = t.optJSONObject("search_results")?.optJSONArray("results")
            if (kq != null) for (j in 0 until kq.length()) {
                val r = kq.optJSONObject(j) ?: continue
                nguon += r.optString("title").ifBlank { "trang web" } to r.optString("url")
            } else nguon += ("Tìm kiếm web" to "")
        }
        return KetQua(chu, nguon) to null
    }

    /** Bỏ Markdown, gộp khoảng trắng — lời đọc ra loa phải là chữ trơn. */
    fun lamSach(s: String): String = s
        .replace(Regex("\\[(\\d+(,\\s*\\d+)*)\\]"), "")         // [1], [2, 3] chú thích nguồn
        .replace(Regex("[*#_`>]+"), "")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Lời đọc: tối đa ba câu, tối đa [Cai.TRAN_DOC_TRA_MANG] ký tự. */
    fun rutGon(s: String): String {
        val cau = Regex("(?<=[.!?])\\s+").split(s).filter { it.isNotBlank() }.take(3)
        var r = cau.joinToString(" ")
        if (r.length > Cai.TRAN_DOC_TRA_MANG) r = r.take(Cai.TRAN_DOC_TRA_MANG).substringBeforeLast(' ') + "…"
        return r
    }
}
