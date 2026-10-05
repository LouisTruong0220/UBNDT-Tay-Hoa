package vn.roboworld.hcc

/**
 * MỌI CON SỐ CHỈNH ĐƯỢC GOM MỘT CHỖ.
 *
 * Vì sao có file này: các con số vận hành (hạn chờ, số lần thử lại, bật/tắt một tầng)
 * là thứ phải chỉnh ngoài hiện trường, thường là chỉnh vội giữa buổi lắp đặt. Rải chúng
 * trong năm file mã thì người sửa phải đọc hiểu cả năm file mới dám đụng.
 *
 * Sửa ở đây rồi build lại APK. Không có gì trong file này cần hiểu mã Kotlin để đổi.
 */
object Cai {

    // ══════════════════ TRỢ LÝ NGOÀI — KHO CHÍNH ══════════════════
    //
    // Anh Trường chốt 18/09/2026: trợ lý ngoài là kho CHÍNH, kho thủ tục nằm trong
    // robot lùi xuống làm lưới đỡ.
    //
    // ⚠ Ba điều đã đo trên máy chủ thật 18/09/2026, phải biết trước khi chỉnh:
    //   ① Máy chủ của họ tự ghi là BẢN XEM TRƯỚC, "không dùng cho công việc thực tế".
    //   ② Phạm vi dữ liệu của họ là Đắk Lắk và Lâm Đồng. Hỏi tỉnh khác thì họ từ chối,
    //      và app sẽ tự rơi về kho trong robot.
    //   ③ Lúc tài khoản của họ hết hạn mức, thông báo lỗi TIẾNG ANH rơi thẳng vào
    //      trường câu trả lời. Bộ lọc locTraLoiNgoai() bên lớp web chặn việc đó.

    /** Bật/tắt cả tầng trợ lý ngoài. Tắt thì app chạy hoàn toàn bằng kho trong robot. */
    const val BAT_TRO_LY_NGOAI = true

    const val URL_TRO_LY_NGOAI = "https://thutuc.hanhchinhso.ai.vn/api/chat/stream"

    /**
     * Hạn chờ trợ lý ngoài (mili giây).
     *
     * ⚠ SỐ ĐO CŨ 46 GIÂY LÀ SAI — đó là mạng yếu chỗ ngồi hôm đo, không phải máy chủ.
     *   Đo lại 18/09/2026 trên mạng tốt, cùng những câu hỏi ấy:
     *
     *       Đăng ký tạm trú cần giấy tờ gì?          2.718 ký tự    0,7 giây
     *       Đăng ký khai tử cần giấy tờ gì?          2.823 ký tự    0,5 giây
     *       Chứng thực chữ ký cần giấy tờ gì?        3.087 ký tự    0,2 giây
     *
     *   Nghĩa là hỏi TRỰC TIẾP lúc người dân đứng trước robot hoàn toàn khả thi.
     *
     * ⚠ NHƯNG máy chủ của họ có HAI đường, và chỉ một đường nhanh:
     *     · khớp kho văn bản  → 0,2–0,7 giây, trả kèm citations — đường này dùng được
     *     · không khớp        → hỏi mô hình ngôn ngữ, 3,5–5,6 giây, và chính họ gắn
     *                           dòng "CHƯA trích dẫn được căn cứ pháp lý"
     *   Đường thứ hai bị locTraLoiNgoai() vứt, nên hạn 9 giây chỉ còn là lưới an toàn
     *   cho lúc mạng ở trung tâm chập chờn — không phải con số phải tính toán nữa.
     *
     * Quá hạn KHÔNG phải là hỏng: app rơi về kho trong robot và vẫn trả lời được.
     */
    const val HAN_CHO_NGOAI_MS = 9_000

    /** Hạn nối mạng riêng, ngắn hơn hạn đọc: mất Wi-Fi thì biết ngay, khỏi chờ phí. */
    const val HAN_NOI_MS = 4_000

    /** Chờ nguồn quá mốc này thì robot nói trước LOI_DANG_TRA (đo 24/09: đứng im 10 giây). */
    const val CHO_TRUOC_KHI_BAO_MS = 2_000
    const val LOI_DANG_TRA = "Dạ, anh chị chờ tôi tra thêm một chút."

    /**
     * ⚠ PHẢI đặt User-Agent tường minh.
     * Bài học 27/08/2026 (08-research/13-tro-ly-mang-groq-gemini): Android gửi
     * "Dalvik/2.1.0" và nhiều nhà cung cấp chặn thẳng nhóm đó — trả 403 sau 0,14 giây,
     * nhìn y hệt lỗi mạng. Đặt tên rõ ràng cũng là phép lịch sự với bên cho dùng API:
     * họ nhìn log là biết ai đang gọi.
     */
    const val USER_AGENT = "RoboworldNova/1.0 (robot le tan hanh chinh cong)"

    // ══════════════════ MIC ══════════════════
    /** Im lặng quá mức này thì coi là dứt câu → tắt mic và gửi đi. */
    const val CHO_DUT_CAU_MS = 1_800L

    /** Bấm mic rồi bỏ đi — trần cứng để mic không mở mãi. */
    const val TRAN_MO_MIC_MS = 15_000L

    /** Thăm dò lại xem mô hình gọi được chưa. */
    const val THAM_DO_LAI_MS = 20_000L

    // XOAY THEO NGƯỜI: xem khối cuối file (24/09/2026, DoiDien.kt).

    // ══════════════════ TRA CỨU TRÊN MẠNG — câu đời thường ══════════════════
    //
    // Anh Trường yêu cầu 22/09/2026. Chỉ cho câu KHÔNG thuộc thủ tục · pháp luật ·
    // chính trị — phân loại ở MainApplication.nenTraMang(). Tắt hẳn nếu thẻ nhớ chưa có
    // files/cau-hinh/tra-mang.txt (khoá không bao giờ nằm trong APK).

    /** Công tắc tổng. Để true vẫn an toàn: chưa có khoá trên thẻ nhớ là tầng tự tắt. */
    const val BAT_TRA_MANG = true

    /** Hạn chờ một lượt tra (mili giây). Người dân đứng trước robot chờ được ~8 giây. */
    const val HAN_CHO_TRA_MANG_MS = 9_000

    /** Model mặc định khi file khoá chỉ có một dòng. Đừng dùng bí danh "-latest". */
    const val MODEL_TRA_MANG_GEMINI = "gemini-2.5-flash"
    const val MODEL_TRA_MANG_GROQ = "groq/compound-mini"

    /** Lời ĐỌC tối đa bao nhiêu ký tự — màn hình vẫn hiện bản đầy đủ. */
    const val TRAN_DOC_TRA_MANG = 300

    // ══════════════════ CHẾ ĐỘ AgentOS — THỬ NGHIỆM 22/09/2026 ══════════════════
    //
    // Bật: AgentOS lo hiểu câu và hội thoại; gặp câu thủ tục thì gọi Action
    // vn.roboworld.hcc.TRA_CUU_THU_TUC, app tra DỮ LIỆU DỰ ÁN rồi trả về cho AgentOS.
    // Tắt: app tự điều phối như trước (TraLoi.hoi), AgentOS bị cắt ở đầu ra.
    //
    // ⚠ Hãng khuyên KHÔNG dùng AgentOS cho app này (roboworld answer.docx, mục A8):
    //   "AgentOS tối ưu cho nghiệp vụ của chúng tôi… app sẽ phụ thuộc AgentOS".
    // ⚠ TẮT sau khi đo 22/09/2026 — xem README mục "Chế độ AgentOS". Ba lý do đo được:
    //   AgentOS bỏ qua dữ liệu trong ActionResult · tự nghe tiếng nói chuyện trong phòng và
    //   tự đáp dù micro app đóng · còn chen vào sau khi app đã chặn. Bật lại = đổi thành true.
    const val CHE_DO_AGENTOS = true

    /** Có đăng ký KNOWLEDGE_QA không. Đăng ký = AgentOS được đọc kho Portal DÙNG CHUNG. */
    const val GIU_KNOWLEDGE_QA = true

    /**
     * BẢN THỬ 24/09/2026 — anh Trường muốn xem AgentOS trả lời bằng KHO PORTAL.
     * true: không đăng ký Action tra dữ liệu dự án, persona riêng, bỏ lớp "chưa có dữ liệu".
     * ⚠ Kho Portal cấu hình THEO DOANH NGHIỆP — dùng chung mọi robot trong tài khoản.
     * Chỉ có nghĩa khi CHE_DO_AGENTOS = true. Về bản thường: cả ba công tắc về false.
     */
    const val CHE_DO_PORTAL = true

    /**
     * ĐƯỜNG LUI của chế độ AgentOS (29/09/2026). Robot SN M03SHW2A30025032R8C7 nghe đúng câu
     * rồi đứng mãi ở "Để tôi xem…": AgentOS im thì app không làm gì cả. Quá mốc này mà
     * AgentOS chưa nói chữ nào thì app tự trả lời bằng dữ liệu nạp sẵn (TraLoi.hoi).
     * Robot cũ trả lời sau 5–12 giây (đo 24/09) nên 15 giây không cướp lời AgentOS.
     */
    const val CHO_AGENTOS_MS = 15_000L

    // ══════════════════ DI CHUYỂN — anh Trường yêu cầu 29/09/2026 ══════════════════
    // Ba việc, cùng do DiChuyen.kt điều phối: về trạm sạc · du hành · về điểm lễ tân.

    /** Mật khẩu nút "Về trạm sạc" trên màn chính. Pin yếu thì robot tự về, KHÔNG hỏi. */
    const val MAT_KHAU_VE_SAC = "4030"
    /** Pin dưới mức này (và chưa cắm sạc) → bỏ mọi việc, tự về trạm sạc. */
    const val PIN_VE_SAC = 20
    /** Về sạc vì pin yếu thì sạc tới mức này mới rời trạm ra điểm lễ tân. */
    const val PIN_DI_TIEP = 90
    /** Hạn một chuyến về sạc — quá thì báo lỗi, không để robot dò cọc mãi. */
    const val HAN_VE_SAC_MS = 180_000L

    /** Du hành: đi vòng liên tục qua các điểm này, theo đúng thứ tự, KHÔNG nói gì. */
    val DIEM_DU_HANH = listOf("Diem 1", "Diem 2", "Diem 3")

    /** Điểm đón khách. Không ai tương tác quá CHO_VE_LE_TAN_MS → robot tự về đây đứng. */
    const val DIEM_LE_TAN = "Le tan"
    const val CHO_VE_LE_TAN_MS = 60_000L

    // ══════════════════ XOAY THEO NGƯỜI — 24/09/2026 ══════════════════
    // Rời màn chờ → robot tìm người gần nhất phía trước, xoay cả thân đối diện họ
    // (startFocusFollow). Về màn chờ → thôi theo, xoay về hướng đứng cũ. Xem DoiDien.kt.

    /** Tắt hẳn tính năng bằng một dòng nếu hiện trường thấy khó chịu. */
    const val BAT_XOAY_THEO_NGUOI = true

    /** Chỉ theo người đứng trong bán kính này (mét). Người đứng quầy thao tác cách ~0,5–1 m. */
    const val XOAY_NGUONG_MET = 2.0

    /** Người đang theo biến mất quá lâu thì nhả, tìm người khác. */
    const val XOAY_VANG_MS = 6_000L

    /** lostTimer truyền cho hãng — chưa rõ đơn vị, xem chú thích DoiDien.kt. */
    const val XOAY_MAT_DAU_MS = 8_000L

    /** Tốc độ quay về hướng cũ, độ/giây (hãng cho 0–50). Chậm cho người đứng cạnh khỏi giật mình. */
    const val XOAY_TOC_DO = 25f

    /** Lệch dưới mức này (độ) thì khỏi quay về. */
    const val XOAY_BO_QUA_DO = 8.0
}
