package vn.roboworld.hcc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.coreservice.client.ApiListener
import com.ainirobot.coreservice.client.Definition
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.ainirobot.coreservice.client.listener.CommandListener
import com.ainirobot.coreservice.client.listener.TextListener
import com.ainirobot.coreservice.client.module.ModuleCallbackApi
import com.ainirobot.coreservice.client.speech.SkillApi
import com.ainirobot.coreservice.client.speech.entity.TTSEntity

/**
 * Lớp bọc RobotApi — gom mọi thứ liên quan tới phần cứng robot vào một chỗ.
 *
 * Vì sao cần lớp này (theo tài liệu RobotApi của OrionStar):
 *  - App chỉ được uỷ quyền dùng SDK khi ĐÃ kết nối RobotOS VÀ giao diện đang ở tiền cảnh.
 *  - Khi có sự kiện hệ thống (dừng khẩn cấp, pin yếu, OTA, lỗi phần cứng) thì app bị TREO
 *    (onSuspend) và mọi lệnh gọi API mất tác dụng cho tới khi onRecovery.
 *  - Điều kiện tiên quyết của MỌI lệnh dẫn đường: đã dựng bản đồ, đã định vị thành công,
 *    LiDAR đang bật. Thiếu một trong ba thì startNavigation luôn lỗi.
 */
object RobotHelper {

    private const val TAG = "RobotHelper"
    private val main = Handler(Looper.getMainLooper())

    /** reqId chỉ dùng để lần log. Tài liệu khuyến nghị dùng biến static tự tăng. */
    private var reqId = 1000
    private fun nextReqId(): Int = ++reqId

    @Volatile var daKetNoi = false; private set
    @Volatile var biTreo = false; private set

    /** Được gọi khi quyền điều khiển bị thu hồi / khôi phục, để màn hình tự cập nhật. */
    var onTrangThaiDoi: ((ketNoi: Boolean, treo: Boolean) -> Unit)? = null

    // ───────────────────────── Kết nối ─────────────────────────

    /** Giọng nói nằm ở SkillApi, KHÁC RobotApi — phải kết nối riêng, dễ quên. */
    private val skill = SkillApi()
    @Volatile private var skillSanSang = false

    fun ketNoi(context: Context) {
        skill.connectApi(context, object : ApiListener {
            override fun handleApiConnected() { skillSanSang = true; Log.d(TAG, "Đã kết nối SkillApi (giọng nói)") }
            override fun handleApiDisconnected() { skillSanSang = false }
            override fun handleApiDisabled() { skillSanSang = false; Log.w(TAG, "SkillApi bị từ chối") }
        })

        RobotApi.getInstance().connectServer(context, object : ApiListener {
            override fun handleApiConnected() {
                daKetNoi = true
                biTreo = false
                Log.d(TAG, "Đã kết nối RobotOS")
                // Đặt callback nhận chỉ lệnh giọng nói và sự kiện hệ thống
                RobotApi.getInstance().setCallback(object : ModuleCallbackApi() {
                    override fun onSendRequest(
                        reqId: Int, reqType: String?, reqText: String?, reqParam: String?
                    ): Boolean {
                        Log.d(TAG, "onSendRequest type=$reqType text=$reqText")
                        // Trả false để hệ thống xử lý tiếp như bình thường
                        return false
                    }

                    override fun onSuspend() {
                        biTreo = true
                        Log.w(TAG, "BỊ TREO — hệ thống thu hồi quyền điều khiển")
                        baoTrangThai()
                    }

                    override fun onRecovery() {
                        biTreo = false
                        Log.d(TAG, "Đã khôi phục quyền điều khiển")
                        baoTrangThai()
                    }
                })
                baoTrangThai()
                // Nạp sẵn danh sách điểm để lần dẫn đầu tiên đã dò được tên.
                napDanhSachDiem()
            }

            override fun handleApiDisconnected() {
                daKetNoi = false
                Log.w(TAG, "Mất kết nối RobotOS")
                baoTrangThai()
            }

            override fun handleApiDisabled() {
                daKetNoi = false
                Log.w(TAG, "RobotOS từ chối — app chưa được uỷ quyền. " +
                        "Nhớ khởi động app từ RobotOS Home, không chạy thẳng từ Android Studio.")
                baoTrangThai()
            }
        })
    }

    private fun baoTrangThai() = main.post { onTrangThaiDoi?.invoke(daKetNoi, biTreo) }

    // ───────────────────────── Đọc thành tiếng ─────────────────────────

    /**
     * Đọc một đoạn bằng giọng của robot.
     *
     * ⚠ PHẢI dùng bản playText(TTSEntity, …), KHÔNG dùng playText(String, …).
     *   Bản nhận String đã lỗi thời: ROM trên máy Nova đọc tham số đó bằng Gson và mong
     *   một đối tượng JSON, đưa chuỗi trần vào là nó ném
     *   "JsonSyntaxException: Expected BEGIN_OBJECT but was STRING" — robot IM RE hoàn toàn,
     *   app không hề báo lỗi vì ngoại lệ xảy ra bên phía dịch vụ, không bắn ngược về.
     *   Trình biên dịch chỉ cảnh báo "deprecated", rất dễ bỏ qua.
     *
     * @param khiXong gọi khi đọc xong hoặc bị lỗi — để giao diện đổi lại nút.
     */
    fun doc(vanBan: String, khiXong: () -> Unit) {
        if (!skillSanSang) {
            Log.w(TAG, "SkillApi chưa sẵn sàng, bỏ qua lệnh đọc")
            main.post { khiXong() }
            return
        }
        Log.d(TAG, "Đọc: ${vanBan.take(60)}…")
        /* Ghi nhớ TRƯỚC khi phát: onTTSResult bắn mẩu chữ đầu gần như ngay lập tức, nhớ
           sau là mẩu đầu bị xếp nhầm "AgentOS tự nói" và robot tự cắt tiếng mình. */
        MainApplication.ghiNhoTuNoi(vanBan)
        DiChuyen.coHoatDong()        // đang đọc cho người nghe — chưa được bỏ đi về lễ tân
        skill.playText(TTSEntity(vanBan), object : TextListener() {
            override fun onStart() { Log.d(TAG, "Bắt đầu đọc") }
            override fun onComplete() { Log.d(TAG, "Đọc xong"); DiChuyen.coHoatDong(); main.post { khiXong() } }
            override fun onError() { Log.w(TAG, "Lỗi khi đọc"); main.post { khiXong() } }
            override fun onStop() { main.post { khiXong() } }
        })
    }

    fun dungDoc() {
        if (skillSanSang) runCatching { skill.stopTTS() }
    }

    // ───────────────────────── Định vị ─────────────────────────

    /**
     * Kiểm tra robot đã định vị trên bản đồ chưa.
     * Chưa định vị thì mọi lệnh dẫn đường đều trả lỗi ERROR_NOT_ESTIMATE (-116).
     */
    fun daDinhVi(ketQua: (Boolean) -> Unit) {
        if (!sanSang()) { main.post { ketQua(false) }; return }
        RobotApi.getInstance().isRobotEstimate(nextReqId(), object : CommandListener() {
            override fun onResult(result: Int, message: String?) {
                main.post { ketQua("true" == message) }
            }
        })
    }

    /** Lấy danh sách điểm đã đặt trên bản đồ hiện tại — dùng để tự kiểm khi lắp đặt. */
    fun layDanhSachDiem(ketQua: (String?) -> Unit) {
        if (!sanSang()) { main.post { ketQua(null) }; return }
        RobotApi.getInstance().getPlaceList(nextReqId(), object : CommandListener() {
            override fun onResult(result: Int, message: String?) {
                main.post { ketQua(message) }
            }
        })
    }

    // ─────────────────── Khớp tên điểm bản đồ ────────────────────
    //
    // Vì sao cần: tên điểm do KỸ THUẬT VIÊN gõ tay lúc quét bản đồ, còn tên trong
    // app do người soạn dữ liệu gõ. Hai người, hai bàn phím, một dấu tiếng Việt
    // hoặc một chữ hoa lệch nhau là robot ném ERROR_DESTINATION_NOT_EXIST rồi đứng
    // im — nhìn từ ngoài y hệt máy hỏng, mất cả buổi mới lần ra.
    //
    // Nên trước khi gửi lệnh, app hỏi robot danh sách điểm THẬT rồi tự dò: bỏ dấu,
    // hạ chữ thường, gộp khoảng trắng. Khớp thì dùng ĐÚNG tên robot đang giữ.
    // Không khớp thì vẫn gửi tên khai (không tệ hơn trước) nhưng ghi log rõ ràng.

    /** Danh sách tên điểm lấy về gần nhất. Rỗng nghĩa là chưa hỏi được. */
    @Volatile private var tenDiemTrenBanDo: List<String> = emptyList()

    private fun chuanHoa(s: String): String {
        val bo = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
        return bo.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    /** Đọc danh sách điểm về bộ nhớ. Gọi lúc khởi động và trước mỗi lần dẫn. */
    fun napDanhSachDiem(xong: () -> Unit = {}) {
        layDanhSachDiem { json ->
            tenDiemTrenBanDo = docTenDiem(json)
            Log.d(TAG, "Bản đồ có ${tenDiemTrenBanDo.size} điểm: $tenDiemTrenBanDo")
            xong()
        }
    }

    /** Bóc tên điểm từ JSON getPlaceList. Hãng trả mảng object có trường "name". */
    private fun docTenDiem(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i)
                (o?.optString("name") ?: arr.optString(i)).takeIf { !it.isNullOrBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không đọc được danh sách điểm: ${e.message}")
            emptyList()
        }
    }

    /**
     * Đổi tên điểm khai trong app sang tên THẬT trên bản đồ.
     * Chưa lấy được danh sách, hoặc không tìm ra chỗ nào khớp → trả nguyên tên khai.
     */
    fun tenDiemThat(tenKhai: String): String {
        if (tenDiemTrenBanDo.isEmpty()) return tenKhai
        tenDiemTrenBanDo.firstOrNull { it == tenKhai }?.let { return it }
        val can = chuanHoa(tenKhai)
        val khop = tenDiemTrenBanDo.firstOrNull { chuanHoa(it) == can }
        if (khop != null) {
            Log.d(TAG, "Điểm '$tenKhai' khớp mềm với '$khop' trên bản đồ")
            return khop
        }

        /* ── Lưới đỡ cuối: BÍ DANH ───────────────────────────────────────────
         * Dò mềm ở trên chỉ bỏ DẤU và hạ chữ thường — nó KHÔNG sửa CHÍNH TẢ.
         * Lỗi thật gặp 09/09/2026: bản đồ bệnh viện đặt tên "Khoa chuẩn đoán hình
         * ảnh" (chuẩn đoán là lỗi chính tả rất phổ biến của chẩn đoán), app hỏi
         * "Khoa chan doan hinh anh". Bỏ dấu xong vẫn là hai chuỗi khác nhau, robot
         * ném ERROR_DESTINATION_NOT_EXIST rồi đứng im — nhìn y hệt máy hỏng, và đó
         * là khoa DUY NHẤT không dẫn được nên rất khó đoán ra nguyên nhân.
         *
         * Bảng bí danh khai trong dữ liệu (BI_DANH_DIEM ở dung-du-lieu.py), lớp web
         * đẩy xuống lúc khởi động. Nhờ vậy chữa được bằng cách cài lại app, không
         * phải cử người ra hiện trường sửa tên trên bản đồ.
         */
        for (bd in biDanhCua(tenKhai)) {
            val canBd = chuanHoa(bd)
            val k2 = tenDiemTrenBanDo.firstOrNull { chuanHoa(it) == canBd }
            if (k2 != null) {
                Log.d(TAG, "Điểm '$tenKhai' khớp qua BÍ DANH '$bd' → '$k2' trên bản đồ")
                return k2
            }
        }

        Log.w(TAG, "KHÔNG có điểm nào khớp '$tenKhai'. Bản đồ đang có: $tenDiemTrenBanDo")
        return tenKhai
    }

    /* Bí danh theo tên điểm khai trong app. Khoá đã chuẩn hoá sẵn để tra cho nhanh. */
    @Volatile private var biDanh: Map<String, List<String>> = emptyMap()

    private fun biDanhCua(tenKhai: String): List<String> = biDanh[chuanHoa(tenKhai)] ?: emptyList()

    /**
     * Nạp bảng bí danh từ lớp web. JSON dạng {"Khoa chan doan hinh anh": ["Khoa chuan doan hinh anh", …]}.
     * Gọi một lần lúc khởi động, xem Cau.datBiDanhDiem.
     */
    fun napBiDanh(json: String?) {
        if (json.isNullOrBlank()) return
        val m = HashMap<String, List<String>>()
        runCatching {
            val o = org.json.JSONObject(json)
            for (k in o.keys()) {
                val arr = o.optJSONArray(k) ?: continue
                val ds = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { t -> t.isNotBlank() } }
                if (ds.isNotEmpty()) m[chuanHoa(k)] = ds
            }
        }.onFailure { Log.w(TAG, "Không đọc được bảng bí danh: ${it.message}") }
        biDanh = m
        Log.d(TAG, "Đã nạp bí danh cho ${m.size} điểm")
    }

    // ───────────────────────── Dẫn đường ─────────────────────────

    /**
     * Dẫn khách tới một điểm đã đặt tên trên bản đồ.
     *
     * @param tenDiem  tên điểm khai trong app; hàm tự dò sang tên thật trên bản đồ
     * @param khiToiNoi  gọi khi tới nơi thành công
     * @param khiLoi     gọi kèm câu giải thích tiếng Việt đã dịch sẵn từ mã lỗi
     * @param khiCapNhat gọi khi có sự kiện dọc đường (gặp vật cản, vật cản đã dọn…)
     */
    fun dieuHuongToi(
        tenDiem: String,
        khiToiNoi: () -> Unit,
        khiLoi: (String) -> Unit,
        khiCapNhat: (String) -> Unit = {}
    ) {
        if (!sanSang()) { main.post { khiLoi("Robot chưa sẵn sàng — chưa kết nối RobotOS.") }; return }

        /* Dò sang tên THẬT trên bản đồ. Chưa nạp được danh sách thì dùng nguyên tên
           khai — không tệ hơn cách cũ, và log sẽ nói rõ đã gửi tên nào.

           ⚠ Dò KHÔNG RA thì HỎI LẠI ROBOT rồi thử một lần nữa. Danh sách điểm trước
             đây chỉ nạp đúng một lần lúc nối RobotOS, nên kỹ thuật viên vừa đặt thêm
             điểm hoặc vừa sửa tên trên bản đồ thì app vẫn giữ danh sách cũ cho tới
             khi có người tắt mở lại app. Ngoài hiện trường thì việc đó trông y hệt
             "sửa tên rồi mà vẫn không dẫn được" — và người ta sẽ đi sửa tên lần nữa.
             Chỉ hỏi lại khi dò trượt, nên lần dẫn bình thường không tốn thêm gì. */
        if (khongDoRa(tenDiem)) {
            Log.d(TAG, "Dò '$tenDiem' không ra — hỏi lại robot danh sách điểm rồi thử lần nữa")
            napDanhSachDiem { goiLenhDi(tenDiem, khiToiNoi, khiLoi, khiCapNhat) }
            return
        }
        goiLenhDi(tenDiem, khiToiNoi, khiLoi, khiCapNhat)
    }

    /** Tên khai không khớp điểm nào trên bản đồ — kể cả sau khi dò mềm và dò bí danh. */
    private fun khongDoRa(tenKhai: String): Boolean =
        tenDiemTrenBanDo.isNotEmpty() && tenDiemThat(tenKhai) == tenKhai &&
        tenDiemTrenBanDo.none { chuanHoa(it) == chuanHoa(tenKhai) }

    private fun goiLenhDi(
        tenDiem: String,
        khiToiNoi: () -> Unit,
        khiLoi: (String) -> Unit,
        khiCapNhat: (String) -> Unit
    ) {
        val ten = tenDiemThat(tenDiem)
        maLoiCuoi = 0                // mã lỗi của chuyến trước không được dính sang chuyến này
        // Focus follow CHIẾM GẦM MÁY — tài liệu hãng cấm chạy song song dẫn đường.
        NhayMua.dung(false)
        DoiDien.tat(false)

        // coordinateDeviation 0.2 m và time 30 s là giá trị tài liệu hãng khuyến nghị.
        // Ghi lại MÃ TRẢ VỀ: nếu lệnh bị từ chối ngay thì ActionListener không bao giờ
        // được gọi, không ghi mã này thì robot đứng im mà không có lấy một dòng log.
        val ma = RobotApi.getInstance().startNavigation(
            nextReqId(), ten, 0.2, 30_000L,
            object : ActionListener() {

                override fun onResult(status: Int, response: String?) {
                    main.post {
                        if (status == Definition.RESULT_OK && "true" == response) khiToiNoi()
                        else khiLoi("Dẫn đường không thành công. Mời anh chị chờ nhân viên hỗ trợ.")
                    }
                }

                override fun onError(errorCode: Int, errorString: String?) {
                    maLoiCuoi = errorCode
                    main.post { khiLoi(dichMaLoi(errorCode)) }
                }

                /**
                 * Dọc đường robot bắn ra rất nhiều trạng thái. Ba nhóm phải phân biệt rõ:
                 *  - tạm thời (tránh vật cản)  -> chỉ thông báo, vẫn đang đi
                 *  - HỎNG HẲN (không tìm được đường, ra ngoài bản đồ) -> phải báo THẤT BẠI,
                 *    nếu không nút cứ đứng ở "đang đi" mãi mà robot thì không nhúc nhích
                 *  - đã tới nơi
                 */
                override fun onStatusUpdate(status: Int, data: String?) {
                    Log.d(TAG, "Dẫn đường onStatusUpdate: status=$status data=$data")
                    when (status) {
                        // ── hỏng hẳn ──
                        Definition.STATUS_NAVI_GLOBAL_PATH_FAILED -> main.post {
                            khiLoi("Tôi không tìm được đường tới chỗ đó. Có thể lối đi đang bị chắn, " +
                                   "hoặc tôi đang đứng ngoài khu vực đã quét bản đồ. " +
                                   "Mời anh chị đi theo biển chỉ dẫn giúp tôi ạ.")
                        }
                        Definition.STATUS_NAVI_OUT_MAP -> main.post {
                            khiLoi("Tôi đang ở ngoài vùng bản đồ nên chưa tự đi được. " +
                                   "Nhờ nhân viên đưa tôi về khu vực phục vụ giúp ạ.")
                        }
                        Definition.STATUS_GOAL_OCCLUDED -> main.post {
                            khiCapNhat("Chỗ đứng ở quầy đang bị chắn, tôi chờ một chút.")
                        }
                        // ── tạm thời ──
                        Definition.STATUS_NAVI_AVOID,
                        Definition.STATUS_NAVI_OBSTACLES_AVOID -> main.post {
                            khiCapNhat("Phía trước đang có vật cản, tôi chờ một chút.")
                        }
                        Definition.STATUS_NAVI_AVOID_END,
                        Definition.STATUS_NAVI_OBSTACLES_DISAPPEAR -> main.post {
                            khiCapNhat("Đường đã thông, tôi đi tiếp.")
                        }
                        Definition.STATUS_DEST_NEAR -> main.post {
                            khiCapNhat("Sắp tới nơi rồi ạ.")
                        }
                        else -> return
                    }
                }
            }
        )
        Log.d(TAG, "startNavigation('$ten') trả mã = $ma" +
                   if (ten != tenDiem) "  (app khai '$tenDiem')" else "")
        if (ma < 0) {
            main.post { khiLoi("Robot chưa nhận lệnh đi (mã $ma). Nhờ nhân viên kỹ thuật kiểm tra giúp ạ.") }
        }
    }

    fun dungDieuHuong() {
        if (!sanSang()) return
        RobotApi.getInstance().stopNavigation(nextReqId())
    }

    /** Mã lỗi của lần dẫn đường hỏng gần nhất — DiChuyen cần phân biệt "đang đứng tại điểm rồi". */
    @Volatile var maLoiCuoi = 0
    fun laDangODich(): Boolean = maLoiCuoi == Definition.ERROR_IN_DESTINATION

    /** Tới điểm rồi thì xoay về đúng hướng đã đặt cho điểm đó trên bản đồ. */
    fun xoayTheoHuongDiem(tenDiem: String) {
        if (!sanSang()) return
        runCatching {
            RobotApi.getInstance().resumeSpecialPlaceTheta(nextReqId(), tenDiemThat(tenDiem),
                object : CommandListener() {
                    override fun onResult(result: Int, message: String?) {
                        Log.d(TAG, "Xoay theo hướng điểm '$tenDiem': result=$result $message")
                    }
                })
        }
    }

    // ───────────────────────── Trạm sạc ─────────────────────────

    /**
     * Tự về cọc sạc và cắm vào.
     *
     * Dùng startNaviToAutoChargeAction chứ KHÔNG đi tới một điểm tên "Tram sac": đi tới toạ độ
     * chỉ đưa robot tới GẦN cọc, động tác lùi vào cắm tiếp điểm là việc của API này (dò hồng
     * ngoại). Chép từ app su-kien-rbw đã chạy thật.
     */
    fun veSac(khiXong: (Boolean, String) -> Unit) {
        lyDoChuaSanSang()?.let { ly -> main.post { khiXong(false, ly) }; return }
        NhayMua.dung(false)
        DoiDien.tat(false)
        val daBao = java.util.concurrent.atomic.AtomicBoolean(false)
        fun bao(ok: Boolean, chu: String) { if (daBao.compareAndSet(false, true)) main.post { khiXong(ok, chu) } }
        val ma = RobotApi.getInstance().startNaviToAutoChargeAction(
            nextReqId(), Cai.HAN_VE_SAC_MS,
            object : ActionListener() {
                override fun onResult(status: Int, response: String?) {
                    Log.d(TAG, "veSac onResult: status=$status response=$response")
                    if (status == Definition.RESULT_OK) bao(true, "Đã về tới trạm sạc.")
                    else bao(false, "Robot chưa cắm được vào trạm sạc. Nhờ cán bộ kỹ thuật kiểm tra giúp.")
                }
                override fun onError(errorCode: Int, errorString: String?) {
                    Log.w(TAG, "veSac onError: $errorCode $errorString")
                    bao(false, "Về trạm sạc không thành công (mã $errorCode). Nhờ cán bộ kỹ thuật kiểm tra giúp.")
                }
                override fun onStatusUpdate(status: Int, data: String?) {
                    Log.d(TAG, "veSac onStatusUpdate: status=$status data=$data")
                }
            }
        )
        Log.d(TAG, "startNaviToAutoChargeAction trả mã = $ma")
        if (ma < 0) bao(false, "Robot chưa nhận lệnh về sạc (mã $ma).")
    }

    fun dungVeSac() {
        if (!sanSang()) return
        runCatching { RobotApi.getInstance().stopAutoChargeAction(nextReqId(), true) }
    }

    /**
     * Rời cọc sạc: tiến 0,2 m ở 0,7 m/s (mặc định trong tài liệu hãng, 17-dieu-khien-pin.md).
     * ⚠ CHƯA ĐO trên Nova. Tài liệu ghi phải disableBattery() trước — app CỐ Ý KHÔNG gọi, vì
     *   quên enableBattery() là tắt luôn cơ chế tự sạc của hệ thống. Lệnh này có hỏng thì
     *   khiXong vẫn chạy và DiChuyen vẫn thử startNavigation.
     */
    fun roiSac(khiXong: () -> Unit) {
        if (!sanSang()) { main.post(khiXong); return }
        val daBao = java.util.concurrent.atomic.AtomicBoolean(false)
        fun xong() { if (daBao.compareAndSet(false, true)) main.post(khiXong) }
        val ma = runCatching {
            RobotApi.getInstance().leaveChargingPile(nextReqId(), 0.7f, 0.2f, object : CommandListener() {
                override fun onResult(result: Int, message: String?) {
                    Log.d(TAG, "leaveChargingPile result=$result $message"); xong()
                }
                override fun onError(errorCode: Int, errorString: String?) {
                    Log.w(TAG, "leaveChargingPile lỗi $errorCode $errorString"); xong()
                }
            })
        }.getOrElse { -1 }
        Log.d(TAG, "leaveChargingPile trả mã = $ma")
        if (ma < 0) xong() else main.postDelayed({ xong() }, 16_000)   // hãng: quá 15 s là hỏng
    }

    /** Dịch mã lỗi dẫn đường thành câu người thường đọc được. */
    private fun dichMaLoi(code: Int): String = when (code) {
        Definition.ERROR_NOT_ESTIMATE ->
            "Robot chưa định vị được trên bản đồ. Nhờ nhân viên kỹ thuật định vị lại giúp."
        Definition.ERROR_DESTINATION_NOT_EXIST ->
            "Chưa có điểm này trên bản đồ. Nhờ nhân viên kỹ thuật đặt điểm giúp."
        Definition.ERROR_IN_DESTINATION ->
            "Mình đang ở ngay tại vị trí đó rồi ạ."
        Definition.ERROR_DESTINATION_CAN_NOT_ARRAIVE ->
            "Đường đi đang bị chắn nên tôi không tới được. Mời anh chị đi theo biển chỉ dẫn."
        Definition.ACTION_RESPONSE_ALREADY_RUN,
        Definition.ACTION_RESPONSE_REQUEST_RES_ERROR ->
            "Tôi đang bận một việc khác. Anh chị chờ tôi xong việc rồi bấm lại nhé."
        else -> "Có trục trặc khi dẫn đường (mã $code). Mời anh chị chờ nhân viên hỗ trợ."
    }

    private fun sanSang(): Boolean = lyDoChuaSanSang() == null

    /**
     * Vì sao chưa điều khiển được robot — trả null nghĩa là sẵn sàng.
     * Viết bằng tiếng người thường để đọc thẳng cho dân nghe, không phải mã lỗi.
     */
    fun lyDoChuaSanSang(): String? = when {
        !daKetNoi -> {
            Log.w(TAG, "Chưa kết nối RobotOS")
            "Tôi chưa kết nối được với hệ thống của robot. Nhờ anh chị báo cán bộ kỹ thuật giúp ạ."
        }
        biTreo -> {
            Log.w(TAG, "Đang bị treo — app chưa được uỷ quyền hoặc hệ thống đang bận")
            "Lúc này tôi chưa được phép tự đi. Nhờ nhân viên mở ứng dụng từ màn hình chính " +
            "của robot giúp ạ."
        }
        else -> null
    }
}
