package vn.roboworld.hcc

import android.util.Log
import com.ainirobot.agent.base.llm.LLMMessage
import com.ainirobot.agent.base.llm.Role
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * BỘ ĐIỀU PHỐI HỘI THOẠI — app tự cầm trịch, mô hình chỉ là một lời gọi CÓ KIỂM.
 *
 * Chép nguyên lối làm đã chạy được ở app tra cứu thủ tục Mông Dương (11/08/2026),
 * đổi phần dữ liệu sang khoa phòng bệnh viện và thêm một lớp chặn mà bên kia không cần.
 *
 * Vì sao KHÔNG để AgentOS tự hoạch định: hai chuyện hỏng đã đo được trên máy thật ở
 * app kia, không phải phỏng đoán —
 *   · hỏi giấy khai sinh, mô hình KHÔNG gọi Action mà tự bịa ra danh sách giấy tờ
 *   · gọi Action xong, nhận đủ nội dung, rồi IM LUÔN vì tưởng việc đã xong
 * Chính hãng cũng khuyên vậy (phản hồi 11/08/2026, câu A8): *"AgentOS is optimized for
 * our own business scenarios, so its NLP results may not be sufficiently accurate."*
 *
 * Đường đi bây giờ:
 *
 *   nghe được câu
 *     → ① CẤP CỨU?        → hô ngay, không hỏi mô hình
 *     → ② hỏi bệnh?        → từ chối, không hỏi mô hình
 *     → ③ chưa có dữ liệu? → nói thẳng chưa có, không hỏi mô hình
 *     → ④ tra kho khoa phòng tại chỗ, lấy cả MỨC TIN CẬY
 *     → ⑤ kho hỏi–đáp chung trong APK khớp chắc? → đọc nguyên văn, không hỏi mô hình
 *     → ⑥ dựng prompt kèm ĐÚNG danh sách ứng viên → AgentCore.llm()
 *     → ⑦ KIỂM câu trả lời (dòng THU_TUC_ID) → mới cho robot mở miệng
 *
 * Bốn trong bảy bước trên KHÔNG hỏi mô hình lần nào. Đó là chủ ý: mỗi câu tự trả lời
 * được là một câu không thể bịa, không tốn token, và không chết theo đường truyền.
 *
 * Nguyên tắc xuyên suốt: **việc an toàn chặn bằng MÃ, không chặn bằng lời dặn trong
 * prompt.** Prompt vẫn viết đầy đủ để mô hình cư xử đúng phần lớn thời gian, nhưng
 * không được tính là một lớp bảo vệ.
 */
object TraLoi {

    private const val TAG = "BVTraLoi"

    /** Mô hình chạy trên mạng, tra cứu chạy trên WebView — không được chặn luồng gọi. */
    private val tho = Executors.newSingleThreadExecutor()

    /* ── Những câu app TỰ soạn. Không câu nào đi qua mô hình. ── */

    private const val KHONG_CO_TRONG_KHO =
        "Việc này tôi chưa có trong dữ liệu nên không dám đoán, sợ nói sai thì anh chị mang " +
        "thiếu giấy tờ, phải đi lại lần nữa. Mời anh chị hỏi quầy hướng dẫn giúp tôi ạ."

    private const val MO_HINH_HONG =
        "Xin lỗi anh chị, lúc này tôi chưa nghĩ ra câu trả lời. " +
        "Anh chị chạm vào màn hình để tự tra, hoặc hỏi quầy hướng dẫn giúp tôi ạ."

    /**
     * Điểm vào duy nhất. Gọi được từ luồng bất kỳ — bên trong tự đẩy sang luồng phụ.
     * Cả lời nói (onASRResult) lẫn chữ gõ (Cau.hoiRobot) đều vào đây.
     */
    fun hoi(cauHoi: String) {
        val cau = cauHoi.trim()
        if (cau.length < 2) return
        tho.execute { xuLy(cau) }
    }

    /**
     * Hỏi trợ lý ngoài và đọc câu của nó — KHO CHÍNH.
     *
     * @return true nếu đã trả lời xong (tầng trên dừng ở đây),
     *         false nếu không dùng được và luồng phải rơi về kho trong robot.
     *
     * Ba hàng rào trước khi một chữ nào ra loa:
     *   ① TroLyNgoai.coDauHieuHong()  — chặn ngay ở Kotlin, cho câu báo hết hạn mức
     *   ② Cau.locCauNgoai()           — bộ lọc chính bên lớp web: bóc markup, bóc lựa
     *                                   chọn, chặn câu từ chối ngoài phạm vi
     *   ③ MainApplication.tuDoc()     — cửa ra duy nhất, còn kiểm choPhepNoi()
     */
    /**
     * Tra mạng một câu đời thường. Trả false nếu không dùng được câu trả lời.
     *
     * Ba hàng rào bằng MÃ, theo thứ tự:
     *   ① KHÔNG CÓ NGUỒN THÌ KHÔNG NÓI — đo 22/09 mô hình tự bịa địa danh khi không tra
     *   ② câu trả lời trôi sang chính trị thì vứt
     *   ③ lời đọc rút về tối đa ba câu; màn hình mới hiện bản đầy đủ + nguồn
     * Rồi mới qua tuDoc() — cửa ra duy nhất, còn kiểm choPhepNoi().
     */
    /**
     * CHẾ ĐỘ AgentOS: tra DỮ LIỆU DỰ ÁN cho Action, KHÔNG nói — AgentOS sẽ nói.
     * Cùng hai nguồn như đường thường: kho nạp sẵn trước, nguồn thutuc sau.
     * Trả (bản đầy đủ, bản rút gọn, nguồn JSON) hoặc null.
     */
    fun cauKhongCo() = KHONG_CO_TRONG_KHO

    /**
     * LLM chọn mục kho khớp với câu người dân nói tự nhiên. Trả `hoi` của mục, hoặc null.
     * ⚠ Hạn chờ 4 giây, không phải 20: đo 22/09 lúc mất mạng, chờ 20 giây mới báo "chưa có".
     */
    private fun chonMucKho(cau: String): String? {
        val ds = runCatching { JSONArray(Cau.dsCauKho()) }.getOrNull() ?: return null
        if (ds.length() == 0) return null
        val sb = StringBuilder()
        for (i in 0 until ds.length()) sb.append(i + 1).append(". ").append(ds.getJSONObject(i).optString("ten")).append('\n')
        val loiDan =
            "Bạn là bộ phân loại câu hỏi của robot hướng dẫn thủ tục hành chính cấp xã. " +
            "Dưới đây là danh sách thủ tục robot có dữ liệu. Người dân hỏi bằng lời tự nhiên, " +
            "có thể nói sai chữ, thiếu chữ, hoặc kể hoàn cảnh thay vì gọi tên thủ tục. " +
            "Hãy chọn ĐÚNG MỘT thủ tục người dân đang cần, và CHỈ trả lời bằng SỐ THỨ TỰ của thủ " +
            "tục đó. Nếu câu hỏi không liên quan tới thủ tục nào trong danh sách, hoặc chưa đủ rõ " +
            "để chọn một thủ tục, trả lời đúng một chữ: KHONG. Không giải thích gì thêm.\n\n" + sb
        val tin = listOf(LLMMessage(Role.USER, loiDan + "\nCâu người dân: \"" + cau + "\"\nTrả lời:"))
        val khoa = java.util.concurrent.CountDownLatch(1)
        var tra: String? = null
        val t0 = System.currentTimeMillis()
        // Đo có mạng 22/09: 0,82–1,0 giây. 4 giây là dư; mất mạng thì bỏ nhanh.
        MainApplication.hoiMoHinh(tin, 4_000L) { chu, _ -> tra = chu; khoa.countDown() }
        if (!khoa.await(5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "LLM chọn mục: quá hạn"); return null
        }
        val chu = tra?.trim().orEmpty()
        val so = Regex("^\\D{0,6}(\\d{1,3})\\b").find(chu)?.groupValues?.get(1)?.toIntOrNull()
        val ms = System.currentTimeMillis() - t0
        if (so == null || so !in 1..ds.length()) {
            Log.d(TAG, "LLM chọn mục: KHÔNG (${ms} ms) — '${chu.take(30)}'")
            return null
        }
        val o = ds.getJSONObject(so - 1)
        Log.d(TAG, "LLM chọn mục: #$so → ${o.optString("ten").take(60)} (${ms} ms)")
        return o.optString("hoi")
    }

    fun traDuLieu(cau: String): Triple<String, String, String>? {
        val nt = runCatching { JSONObject(Cau.traKhoNapTruoc(cau)) }.getOrNull()
        if (nt != null && nt.optBoolean("dung") && nt.optString("doc").isNotBlank()) {
            Log.d(TAG, "traDuLieu: kho nạp sẵn")
            return Triple(nt.optString("hien"), nt.optString("doc"),
                          nt.optJSONArray("nguon")?.toString() ?: "[]")
        }
        val khoa = java.util.concurrent.CountDownLatch(1)
        var tho: String? = null
        TroLyNgoai.hoi(cau) { t, _ -> tho = t; khoa.countDown() }
        if (!khoa.await(Cai.HAN_CHO_NGOAI_MS.toLong() + 1_000L,
                        java.util.concurrent.TimeUnit.MILLISECONDS)) return null
        val chu = tho ?: return null
        if (TroLyNgoai.coDauHieuHong(chu)) return null
        val loc = runCatching { JSONObject(Cau.locCauNgoai(chu)) }.getOrNull() ?: return null
        if (!loc.optBoolean("dung")) return null
        Log.d(TAG, "traDuLieu: nguồn")
        return Triple(chu, loc.optString("doc"), TroLyNgoai.canCuCuoi)
    }

    private fun hoiTraMang(cau: String): Boolean {
        val khoa = java.util.concurrent.CountDownLatch(1)
        var kq: TraMang.KetQua? = null
        var loi: String? = null
        TraMang.hoi(cau) { r, l -> kq = r; loi = l; khoa.countDown() }
        val kip = khoa.await(Cai.HAN_CHO_TRA_MANG_MS.toLong() + 1_000L,
                             java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!kip) { ghiNhatKy(cau, "mang-qua-han", "", "", null); return false }
        val r = kq
        if (r == null) { ghiNhatKy(cau, "mang-hong", "", loi ?: "", null); return false }

        val sach = TraMang.lamSach(r.chu)
        if (sach.isBlank()) { ghiNhatKy(cau, "mang-rong", "", "", null); return false }
        if (r.nguon.isEmpty()) {                                           // ①
            Log.w(TAG, "Tra mạng: câu trả lời KHÔNG kèm nguồn — vứt: ${sach.take(80)}")
            ghiNhatKy(cau, "mang-khong-nguon", "", sach.take(120), null)
            return false
        }
        if (MainApplication.dapMangCoChinhTri(sach)) {                     // ②
            ghiNhatKy(cau, "mang-chinh-tri", "", sach.take(120), null)
            return false
        }

        val nguonJson = JSONArray().apply {
            r.nguon.distinctBy { it.first }.take(4).forEach { (ten, url) ->
                put(JSONObject().put("title", ten).put("url", url).put("loai", "web"))
            }
        }.toString()
        Cau.hienBanDayDu(
            "**Thông tin tra trên Internet** — chỉ để tham khảo, không phải thông tin " +
            "của Trung tâm.\n\n" + sach, nguonJson)
        MainApplication.tuDoc(TraMang.rutGon(sach))                       // ③
        ghiNhatKy(cau, "mang", "", "${r.nguon.size} nguồn", null)
        return true
    }

    private fun hoiTroLyNgoai(cau: String): Boolean {
        val khoa = java.util.concurrent.CountDownLatch(1)
        var tho2: String? = null
        var loi: String? = null

        TroLyNgoai.hoi(cau) { traLoi, viSao ->
            tho2 = traLoi; loi = viSao; khoa.countDown()
        }

        /* Chờ có giới hạn. Quá hạn thì BỎ, không huỷ lời gọi đang bay — nó về muộn
           cũng không ai đọc, vì biến cục bộ ở đây đã hết vai trò. */
        /* Nguồn có sẵn câu thì về trong 0,2–0,7 giây; chưa có thì 6–40 giây. Đo 24/09: robot
           ĐỨNG IM 10 giây trước mặt người dân. Quá 2 giây chưa về thì nói trước một câu —
           chỉ phát tiếng, không đẩy lên màn (màn đã có dòng "đang tra"). */
        var kip = khoa.await(Cai.CHO_TRUOC_KHI_BAO_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!kip) {
            if (MainApplication.choPhepNoi()) {
                Log.d(TAG, "Nguồn chưa về sau ${Cai.CHO_TRUOC_KHI_BAO_MS}ms — báo người dân chờ")
                RobotHelper.doc(Cai.LOI_DANG_TRA) {}
            }
            kip = khoa.await(Cai.HAN_CHO_NGOAI_MS.toLong() + 1_000L - Cai.CHO_TRUOC_KHI_BAO_MS,
                             java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        if (!kip) {
            Log.w(TAG, "Trợ lý ngoài quá hạn — rơi về kho trong robot")
            ghiNhatKy(cau, "ngoai-qua-han", "", "quá hạn chờ", null)
            return false
        }
        val chu = tho2
        if (chu.isNullOrBlank()) {
            ghiNhatKy(cau, "ngoai-hong", "", loi ?: "không rõ", null)
            return false
        }

        // ① Hàng rào Kotlin — thứ tuyệt đối không được ra loa
        if (TroLyNgoai.coDauHieuHong(chu)) {
            Log.w(TAG, "Trợ lý ngoài trả về câu có dấu hiệu lỗi hạ tầng — bỏ")
            ghiNhatKy(cau, "ngoai-loi-ha-tang", "", chu.take(120), null)
            return false
        }

        // ② Bộ lọc chính bên lớp web
        val loc = runCatching { JSONObject(Cau.locCauNgoai(chu)) }.getOrNull()
        if (loc == null || !loc.optBoolean("dung")) {
            val viSao = loc?.optString("vi_sao") ?: "không đọc được kết quả lọc"
            Log.w(TAG, "Bộ lọc bỏ câu của trợ lý ngoài: $viSao")
            ghiNhatKy(cau, "ngoai-bi-loc", "", viSao, null)
            return false
        }

        val doc = loc.optString("doc")
        if (doc.isBlank()) {
            ghiNhatKy(cau, "ngoai-rong", "", "", null)
            return false
        }

        /* ③ ĐẨY BẢN ĐẦY ĐỦ LÊN MÀN HÌNH — trước khi mở miệng.
         *
         * ⚠ Thiếu dòng này thì robot ĐỌC được câu trả lời mà màn hình đứng nguyên ở
         *   "Đang tra trong kho văn bản…". Đã dính đúng thế trên máy thật 18/09/2026:
         *   log ghi "trả lời sau 506ms", loa đọc rõ, mà người dân nhìn màn thì tưởng
         *   máy treo. Kiểu hỏng tệ nhất — mọi tầng đều báo thành công.
         *
         * MÀN HÌNH HIỆN ĐỦ, MIỆNG NÓI GỌN: gửi lên màn câu THÔ (`chu`) chứ không phải
         * `loc.hien` — lớp web tự dựng tiêu đề, gạch đầu dòng và khối căn cứ từ khuôn
         * Markdown của nguồn. `loc.hien` đã bóc sạch ** rồi, gửi nó lên là màn hình ra
         * một khối chữ phẳng lì, mất hết chỗ ngắt.
         *
         * Gọi TRƯỚC tuDoc() vì lớp web dọn bản cũ mỗi khi có lời mới. */
        Cau.hienBanDayDu(chu, TroLyNgoai.canCuCuoi)

        /* Có lựa chọn thì đẩy sang màn hình thành nút bấm. Người lớn tuổi không đọc
           lại nổi cả câu dài để chọn bằng miệng — cho họ bấm. */
        val ds = loc.optJSONArray("lua_chon")
        if (ds != null && ds.length() > 0) Cau.hienLuaChonNgoai(ds.toString())

        // ④ Cửa ra duy nhất
        MainApplication.tuDoc(doc)
        /* Không cần app tự nhớ lượt: trợ lý ngoài giữ ngữ cảnh theo sessionId của
           chính nó (xem TroLyNgoai.phien), và hết lượt khách thì Cau.xoaPhienNgoai()
           xoá đi. Giữ thêm một bản nhớ ở đây là hai nguồn ngữ cảnh, sớm muộn lệch. */
        ghiNhatKy(cau, "ngoai", "", "", doc)
        return true
    }

    private fun xuLy(cau: String) {
        // ── Lớp 1: CẤP CỨU. Chặn trước mọi thứ, kể cả trước khi tra cứu.
        if (MainApplication.chanKhanCapNeuCan(cau)) { ghiNhatKy(cau, "cap-cuu", "", "", null); return }

        // ── Lớp 2: hỏi bệnh, hỏi thuốc. Robot không phải bác sĩ.
        if (MainApplication.chanXinQuyetDinhNeuCan(cau)) { ghiNhatKy(cau, "hoi-y-te", "", "", null); return }

        /* ══ Lớp 2a: KHO NẠP TRƯỚC — đường trả lời NHANH NHẤT ══════════════════════
         *
         * Câu trả lời của trợ lý ngoài, đã hỏi sẵn từ trước bằng tools/nap-truoc.py và
         * cất trong máy. Tra mất chưa tới một phần nghìn giây.
         *
         * Vì sao phải nạp trước thay vì hỏi thẳng — đo trên máy chủ của họ 18/09/2026,
         * ngay sau khi hạn mức vừa hồi:
         *      khai sinh đúng hạn   2.885 ký tự   49,8 giây
         *      khai sinh quá hạn    3.524 ký tự   56,5 giây
         *      chứng thực chữ ký    3.087 ký tự   56,0 giây
         * Người dân đứng trước robot đợi được bảy tám giây. 46 giây thì họ đã đi rồi,
         * và ba nghìn ký tự đọc thành tiếng mất ba bốn phút — không ai nghe hết.
         *
         * Nên kho này cho thứ tốt nhất của cả hai bên: nội dung của trợ lý ngoài (có
         * mẫu đơn, số hiệu thông tư, trích dẫn nguồn, đúng địa bàn Đắk Lắk) mà trả lời
         * tức thì, không cần mạng, không tốn lượt gọi nào của ai.
         */
        val napTruoc = runCatching { JSONObject(Cau.traKhoNapTruoc(cau)) }.getOrNull()
        if (napTruoc != null && napTruoc.optBoolean("dung")) {
            val doc = napTruoc.optString("doc")
            if (doc.isNotBlank()) {
                /* Bản ĐẦY ĐỦ lên màn hình, bản RÚT GỌN ra loa. Người dân nghe được ý
                   chính trong mười lăm giây, rồi tự đọc chi tiết trên màn. */
                Cau.hienBanDayDu(napTruoc.optString("hien"), napTruoc.optJSONArray("nguon")?.toString() ?: "[]")
                /* Mục kho là menu hỏi lại thì dựng NÚT BẤM, y như đường trợ lý ngoài.
                   Thiếu dòng này (đo trên robot 22/09/2026): màn hiện chữ menu mà không có
                   nút nào để chạm — người lớn tuổi đứng đó không biết làm gì tiếp. */
                val ds = napTruoc.optJSONArray("lua_chon")
                if (ds != null && ds.length() > 0) Cau.hienLuaChonNgoai(ds.toString())
                MainApplication.tuDoc(doc)
                ghiNhatKy(cau, "nap-truoc", "", "", doc)
                return
            }
        }

        /* ══ Lớp 2b: TRỢ LÝ NGOÀI — KHO CHÍNH ══════════════════════════════════════
         *
         * Anh Trường chốt 18/09/2026: hỏi trợ lý ngoài trước; kho thủ tục nằm trong
         * robot lùi xuống làm lưới đỡ.
         *
         * Đặt SAU hai lớp chặn an toàn và TRƯỚC mọi thứ khác. Lý do thứ tự này:
         *   · Cấp cứu và hỏi bệnh phải chặn bằng mã, không bao giờ gửi ra ngoài —
         *     vừa mất thời gian vừa không ai bảo đảm câu trả lời của họ an toàn.
         *   · Nhưng lớp "mảng chưa nạp" (hộ tịch · chứng thực · cư trú) thì KHÔNG
         *     chặn ở đây nữa: đó đúng là mảng trợ lý ngoài CÓ mà kho trong robot
         *     THIẾU. Chặn trước là tự bịt mất cái hay nhất của việc tích hợp.
         *     Nếu trợ lý ngoài không trả lời được thì luồng rơi xuống dưới, và lớp
         *     chặn đó vẫn đứng nguyên chỗ cũ ở lớp 3.
         *
         * Gọi mạng nên phải CHỜ có giới hạn: luồng này vốn đã là luồng phụ, chặn ở
         * đây không làm treo giao diện, nhưng người dân đứng đợi thì có giới hạn kiên
         * nhẫn — xem Cai.HAN_CHO_NGOAI_MS.
         */
        /* ══ Lớp 2a': LLM HIỂU CÂU → CHỌN MỤC KHO (22/09/2026) ═════════════════════════
         *
         * Kho khớp chữ không ra ("Bố tôi mất rồi thì làm giấy tờ gì" không có chữ "khai tử")
         * thì hỏi LLM: đây là danh sách thủ tục trong máy, người dân đang hỏi cái nào? LLM CHỈ
         * được trả SỐ THỨ TỰ — app kiểm số, rồi đọc NGUYÊN VĂN dữ liệu dự án. Không một chữ
         * nội dung nào đi qua mô hình.
         * Đặt TRƯỚC lớp tra mạng: câu thủ tục nói tự nhiên thường không chứa từ khoá thủ tục
         * nào, để lọt xuống là nó bị xếp nhầm vào "đời thường" rồi đem lên web.
         * Chính trị đứng trước cả bước này — không gửi câu chính trị cho mô hình.
         */
        if (MainApplication.chanChinhTriNeuCan(cau)) { ghiNhatKy(cau, "chinh-tri", "", "", null); return }
        val chon = chonMucKho(cau)
        if (chon != null) {
            val nt2 = runCatching { JSONObject(Cau.traKhoNapTruoc(chon)) }.getOrNull()
            if (nt2 != null && nt2.optBoolean("dung") && nt2.optString("doc").isNotBlank()) {
                Cau.hienBanDayDu(nt2.optString("hien"), nt2.optJSONArray("nguon")?.toString() ?: "[]")
                val ds = nt2.optJSONArray("lua_chon")
                if (ds != null && ds.length() > 0) Cau.hienLuaChonNgoai(ds.toString())
                MainApplication.tuDoc(nt2.optString("doc"))
                ghiNhatKy(cau, "llm-chon-kho", "", chon, nt2.optString("doc"))
                return
            }
        }

        /* ══ Lớp 2c: CHÍNH TRỊ → từ chối · CÂU ĐỜI THƯỜNG → tra mạng (22/09/2026) ═════
         *
         * Đặt TRƯỚC trợ lý ngoài vì câu đời thường ("mai trời có mưa không") mà gửi sang
         * nguồn thủ tục thì mất trắng 9 giây chờ rồi nhận câu từ chối của họ.
         * Câu thủ tục / pháp luật KHÔNG BAO GIỜ tới đường tra mạng — nenTraMang() chặn.
         * Tra mạng không được thì KHÔNG rơi xuống nguồn thủ tục (họ cũng không có), mà
         * nói thẳng là chưa tra được.
         */
        if (MainApplication.chanChinhTriNeuCan(cau)) { ghiNhatKy(cau, "chinh-tri", "", "", null); return }
        if (Cai.BAT_TRA_MANG && MainApplication.nenTraMang(cau) && TraMang.coKhoa()) {
            if (!hoiTraMang(cau)) MainApplication.tuDoc(MainApplication.loiTraMangKhongDuoc())
            return
        }

        /* Câu ĐỜI THƯỜNG (không từ khoá thủ tục nào) mà LLM cũng nói không khớp thủ tục nào
           trong kho → đáp ngay. Nguồn thutuc.hanhchinhso.ai.vn chỉ có thủ tục; hỏi họ câu
           "mai trời có mưa không" là chờ 9 giây quá hạn để nhận một câu từ chối (đo 22/09). */
        if (MainApplication.nenTraMang(cau)) {
            MainApplication.tuDoc(MainApplication.loiTraMangKhongDuoc())
            ghiNhatKy(cau, "ngoai-pham-vi", "", "", null)
            return
        }

        /* ── Lớp 3: việc NỘI BỘ Trung tâm chưa có dữ liệu (giờ mở cửa, cán bộ trực, số điện
           thoại, đặt lịch…). Đứng TRƯỚC nguồn ngoài: nguồn là kho thủ tục toàn quốc, không thể
           biết giờ mở cửa của Tây Hòa — đo 24/09, hỏi họ mất 5,9 giây rồi bộ lọc vứt câu.
           Vẫn đứng SAU kho nạp sẵn + LLM chọn mục, nên câu thủ tục có chữ "đợi bao lâu"
           được kho trả lời trước khi tới đây. */
        if (MainApplication.chanChuaCoDuLieuNeuCan(cau)) { ghiNhatKy(cau, "chua-co", "", "", null); return }

        if (Cai.BAT_TRO_LY_NGOAI && hoiTroLyNgoai(cau)) return

        // ── Lớp 4: tra trong bảng khoa phòng, lấy cả độ tin cậy.
        val kho = runCatching { JSONObject(Cau.traKhoaChoAI(cau)) }.getOrNull()
        if (kho == null) {
            Log.w(TAG, "Không đọc được kết quả tra cứu — trả lời dự phòng")
            MainApplication.tuDoc(MO_HINH_HONG)
            ghiNhatKy(cau, "loi-tra-cuu", "", "", null)
            return
        }

        val muc = kho.optString("muc")
        val ungVien = kho.optJSONArray("ung_vien") ?: JSONArray()

        // ── Lớp 5: đọc đúng mã phòng nhưng mã đó không có thật. Nói thẳng, không hỏi mô hình.
        if (muc == "ma-khong-co") {
            val ma = kho.optString("ma")
            /* ⚠ Dải mã lấy từ DỮ LIỆU, không ghi cứng. Câu này từng ghi thẳng "từ đê một đến
               đê hai mươi tư"; khi bệnh viện bỏ D23–D24 và thêm C1, CĐHA (08/09/2026) thì
               robot vẫn đọc dải cũ — sai mà không tầng nào báo, vì nó chỉ là một chuỗi. */
            val dai = kho.optString("mo_ta_ma_doc")
            val coDai = if (dai.isBlank()) "" else " Ở đây chỉ có mã phòng $dai."
            MainApplication.tuDoc(
                "Tôi không tìm thấy mã thủ tục $ma.$coDai " +
                "Anh chị xem lại giấy tờ, hoặc hỏi quầy hướng dẫn giúp tôi ạ."
            )
            ghiNhatKy(cau, muc, ma, "", null)
            return
        }

        // Chỉ mở trang chỉ đường khi thật sự chắc — mở nhầm còn rối hơn không mở.
        if (muc == "chac" && ungVien.length() > 0) Cau.guiGoiYSangManHinh(cau)

        /* ── Lớp 5b: KHO HỎI–ĐÁP CHUNG, nằm sẵn trong APK ────────────────────────
         *
         * Mấy chuyện không gắn với khoa nào — quy trình khám lần đầu, phạm vi robot đi
         * được, giới thiệu bệnh viện — trước đây chỉ có trên cổng OrionStar. Mà
         * `LLMConfig.fileSearch`, tham số cho phép `llm()` đọc kho tri thức trên cổng,
         * đã bị máy chủ hãng BỎ từ 26/08/2025 (xem MainApplication.hoiMoHinh). Nghĩa là
         * mô hình KHÔNG với tới kho đó nữa, và mấy câu này rơi vào khoảng trống: tra khoa
         * phòng không ra gì, mô hình thì hoặc chịu, hoặc tự bịa.
         *
         * Nay chúng đi theo app-data.json vào assets/ của APK. Lớp web tra rồi trả về ở
         * trường `hoi_dap` — và CHỈ trả khi tra khoa phòng không chắc, nên lớp này không
         * bao giờ cướp việc của màn chỉ đường.
         *
         * ⚠ ĐỌC NGUYÊN VĂN, KHÔNG HỎI MÔ HÌNH. Cùng một luật với trường `doc` của bảng
         *   khoa phòng: câu nào bệnh viện duyệt thì người bệnh nghe đúng câu đó. Đổi lại
         *   còn được hai thứ — trả lời tức thì, và trả lời được cả khi mạng đang chậm.
         */
        val hd = kho.optJSONObject("hoi_dap")
        if (hd != null) {
            val dap = hd.optString("dap")
            if (dap.isNotBlank()) {
                Log.d(TAG, "Trả lời từ kho hỏi–đáp trong máy: ${hd.optString("nhom")}")
                MainApplication.tuDoc(dap)
                ghiNhatKy(cau, "hoi-dap", hd.optString("nhom"), "", null)
                return
            }
            Log.w(TAG, "Mục hỏi–đáp '${hd.optString("nhom")}' rỗng câu đáp — bỏ qua, hỏi mô hình")
        }

        /* Không có ứng viên nào thì đừng hỏi mô hình. Lớp này thừa hưởng từ app bệnh viện
           (danh sách KHOA PHÒNG); ở app HCC danh sách đó luôn rỗng, nên hỏi chỉ tốn 1,3 giây
           có mạng, 20 giây mất mạng (đo 22/09) để nhận câu "không rõ". */
        if (ungVien.length() == 0) {
            MainApplication.tuDoc(KHONG_CO_TRONG_KHO)
            ghiNhatKy(cau, "khong-co", "", "", null)
            return
        }

        // ── Lớp 6: hỏi mô hình, kèm ĐÚNG danh sách ứng viên vừa tra được.
        MainApplication.hoiMoHinh(dungTinNhan(cau, muc, ungVien)) { chu, loi ->
            if (chu == null) {
                Log.w(TAG, "Mô hình không trả lời: $loi")
                // Còn ứng viên thì tự trả lời bằng dữ liệu trong máy, đừng bỏ mặc người bệnh.
                val duPhong = if (ungVien.length() > 0) ungVien.getJSONObject(0).optString("doc")
                              else KHONG_CO_TRONG_KHO
                MainApplication.tuDoc(duPhong.ifBlank { KHONG_CO_TRONG_KHO })
                ghiNhatKy(cau, muc, "", "loi:$loi", null)
            } else {
                docCauTraLoi(cau, muc, ungVien, chu)
            }
        }
    }

    /**
     * Dựng danh sách tin nhắn gửi mô hình.
     *
     * ⚠ CHỈ gửi id, tên thủ tục và quầy. KHÔNG gửi câu `doc`, KHÔNG gửi danh sách giấy
     *   tờ, số ngày, lệ phí — dù JSON có sẵn hết. Những thứ đó lấy nguyên văn từ niêm
     *   yết; gửi cho mô hình là mời nó diễn đạt lại, mà lệch một món giấy tờ thôi là
     *   người dân đi về lấy thêm rồi quay lại lần nữa. App tự ghép vào sau câu dẫn.
     */
    private fun dungTinNhan(cau: String, muc: String, ungVien: JSONArray): List<LLMMessage> {
        val danhSach = if (ungVien.length() == 0) "(máy không tìm được thủ tục nào khớp)"
        else (0 until ungVien.length()).joinToString("\n") { i ->
            val t = ungVien.getJSONObject(i)
            "[id=${t.optInt("id")}] ${t.optString("ten")} — ${t.optString("vi_tri")}"
        }

        val nhac = when (muc) {
            "chac"      -> "Máy khá chắc ứng viên đầu tiên là đúng."
            "chua-chac" -> "Máy CHƯA CHẮC. Nếu bạn cũng chưa chắc thì hãy hỏi lại cho rõ " +
                           "thay vì khẳng định."
            else        -> "Máy không tìm được thủ tục nào khớp."
        }

        return listOf(
            LLMMessage(Role.SYSTEM, MainApplication.PERSONA + "\n\n" + MainApplication.LUAT_TRA_LOI),
            LLMMessage(Role.USER,
                "DANH SÁCH ỨNG VIÊN (máy vừa tra trong bảng thủ tục cài sẵn trong robot):\n" +
                danhSach + "\n\n" + nhac + "\n\n" +
                "Người dân vừa nói: \"" + cau + "\"")
        )
    }

    /* ══════════════════════════════════════════════════════════════════
       CHỐT AN TOÀN — kiểm câu trả lời của mô hình TRƯỚC KHI robot mở miệng
       ══════════════════════════════════════════════════════════════════ */

    /** Dòng cuối bắt buộc, ví dụ `THU_TUC_ID: 3` hoặc `THU_TUC_ID: KHONG_CO`. */
    private val DONG_ID = Regex("""THU_TUC_ID\s*:\s*([A-Za-z0-9_]+)""", RegexOption.IGNORE_CASE)

    private fun docCauTraLoi(cau: String, muc: String, ungVien: JSONArray, chuGoc: String) {
        val khop = DONG_ID.find(chuGoc)
        val ma = khop?.groupValues?.get(1)?.uppercase().orEmpty()
        val chu = DONG_ID.replace(chuGoc, "").trim()

        // Thiếu hẳn dòng mã → coi như không hợp lệ. Sai theo hướng an toàn.
        if (ma.isEmpty()) {
            Log.w(TAG, "Mô hình bỏ dòng THU_TUC_ID — bỏ câu trả lời. Nguyên văn: $chuGoc")
            MainApplication.tuDoc(KHONG_CO_TRONG_KHO)
            ghiNhatKy(cau, muc, "", "thieu-ma", chuGoc)
            return
        }

        if (ma == "TAM_SU") {
            /* Chuyện ngoài lề: cứ để mô hình nói tự nhiên, nó không khẳng định gì về khoa phòng.
               Chặn kèm chữ số — số ở đây chỉ có thể là tầng hoặc mã phòng, hai thứ mô hình
               không được phép tự nói. */
            val an = chu.any { it.isDigit() }
            if (an) Log.w(TAG, "TAM_SU nhưng câu có chữ số — bỏ: $chu")
            MainApplication.tuDoc(if (an) "Dạ vâng ạ." else chu.ifBlank { "Dạ vâng ạ." })
            ghiNhatKy(cau, muc, "TAM_SU", "", chuGoc)
            return
        }

        if (ma == "KHONG_CO") {
            /* Hai tình huống rất khác nhau:
             *  a) Máy không tra ra gì → mô hình hay "giúp thêm" bằng kiến thức nền, đúng cái
             *     đã làm nó bịa giấy tờ ở app kia. Dùng CÂU CỨNG của app.
             *  b) Máy có ứng viên nhưng chưa chắc → mô hình hỏi lại rất đúng việc
             *     ("quý vị đau bụng trên hay bụng dưới ạ?"), mà bảng ứng viên đang hiện sẵn
             *     trên màn hình để người bệnh chọn. Cho nói.
             * Chặn kèm: câu hỏi lại mà có CHỮ SỐ thì vứt. */
            val hoiLai = ungVien.length() > 0 && chu.isNotBlank() && !chu.any { it.isDigit() }
            if (hoiLai) {
                MainApplication.tuDoc(chu)
            } else {
                if (chu.any { it.isDigit() }) Log.w(TAG, "KHONG_CO nhưng câu có chữ số — bỏ: $chu")
                MainApplication.tuDoc(KHONG_CO_TRONG_KHO)
            }
            ghiNhatKy(cau, muc, if (hoiLai) "KHONG_CO-hoi-lai" else "KHONG_CO", "", chuGoc)
            return
        }

        // Mã phải là id của MỘT trong những ứng viên vừa gửi đi.
        val id = ma.toIntOrNull()
        val chon = (0 until ungVien.length())
            .map { ungVien.getJSONObject(it) }
            .firstOrNull { it.optInt("id") == id }

        if (chon == null) {
            Log.w(TAG, "Mô hình trả id LẠC '$ma' — không nằm trong danh sách ứng viên. Bỏ câu trả lời.")
            MainApplication.tuDoc(KHONG_CO_TRONG_KHO)
            ghiNhatKy(cau, muc, "lac:$ma", "", chuGoc)
            return
        }

        /* Hợp lệ: robot đọc câu dẫn của mô hình, RỒI app tự đọc vị trí lấy thẳng từ bảng
           khoa phòng. Tên khoa, số tầng, mã phòng không đi qua mô hình lần nào. */
        val viTri = chon.optString("doc").ifBlank { KHONG_CO_TRONG_KHO }
        val noi = buildString {
            if (chu.isNotBlank()) { append(chu); if (!chu.endsWith(".")) append("."); append(" ") }
            append(viTri)
        }
        MainApplication.tuDoc(noi)
        ghiNhatKy(cau, muc, ma, "", chuGoc)
    }

    /* ══════════════════════════════════════════════════════════════════
       NHẬT KÝ — để hiệu chỉnh ngưỡng và để biết chi phí
       ══════════════════════════════════════════════════════════════════ */

    /**
     * Ghi mỗi lượt hỏi ra thẻ nhớ. Lấy về bằng:
     *
     *   adb pull /sdcard/Android/data/vn.roboworld.hcc/files/nhat-ky
     *
     * Dùng getExternalFilesDir nên KHÔNG cần xin quyền lưu trữ, và gỡ app là dọn sạch theo.
     *
     * ⚠ Ở bệnh viện, file này có thể chứa lời người bệnh kể về bệnh tình mình. Đó là dữ
     *   liệu riêng tư. Lấy về để hiệu chỉnh xong thì XOÁ, đừng để tồn trên máy, và đừng
     *   gửi ra ngoài Roboworld khi chưa hỏi bệnh viện.
     */
    private fun ghiNhatKy(cau: String, muc: String, ma: String, loi: String, traLoi: String?) {
        runCatching {
            val ctx = MainApplication.boiCanh ?: return
            val thuMuc = File(ctx.getExternalFilesDir(null), "nhat-ky").apply { mkdirs() }
            val ngay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val gio = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val dong = listOf(gio, muc, ma, loi, cau.replace('\n', ' '),
                              (traLoi ?: "").replace('\n', ' ').take(400))
                .joinToString("\t") + "\n"
            File(thuMuc, "hoi-$ngay.tsv").appendText(dong)
        }.onFailure { Log.w(TAG, "Không ghi được nhật ký: ${it.message}") }
    }
}
