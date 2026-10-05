package vn.roboworld.hcc

import android.util.Log
import com.ainirobot.coreservice.client.Definition
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.ainirobot.coreservice.client.listener.CommandListener
import com.ainirobot.coreservice.client.listener.Person
import com.ainirobot.coreservice.client.listener.PersonInfoListener

/**
 * ĐỐI DIỆN NGƯỜI ĐANG THAO TÁC — anh Trường yêu cầu 24/09/2026.
 *
 * Rời màn chờ (người dân chạm màn hình) → robot bật chế độ FOCUS: tìm người GẦN NHẤT phía
 * trước, xoay cả thân về phía họ và giữ hướng theo họ suốt lúc họ đứng thao tác.
 * Quay về màn chờ → thôi theo, xoay về hướng đứng ban đầu.
 *
 * ── HAI THỨ MƯỢN CỦA HÃNG ─────────────────────────────────────────────────
 * ① startGetAllPersonInfo → onData(List<Person>): id · angle · distance · isWithFace.
 * ② startFocusFollow(reqId, personId, lostTimer, maxDistance, listener) — hãng tự lái đầu
 *    + gầm máy tới khi robot đối diện thẳng người đó. Soi bytecode robotservice.jar
 *    24/09: bản 5 tham số gọi sang bản có cờ allowMoveBody = TRUE → xoay cả thân.
 *    ĐỪNG tự tính góc rồi gọi rotate liên tục: người nhúc nhích là robot giật từng nấc.
 *
 * ⚠ Tài liệu hãng (13-kich-ban-co-ban.md):
 *   · focus follow CHIẾM GẦM MÁY — cấm chạy song song dẫn đường. RobotHelper gọi tat()
 *     trước startNavigation.
 *   · KHÔNG gọi lặp startFocusFollow cho cùng một người — chỉ gọi lại sau khi mất dấu.
 *
 * ⚠ CHƯA RÕ ĐƠN VỊ lostTimer: tài liệu ghi "thường điền 5–10 giây" mà kiểu là long.
 *   Truyền 8000: hiểu là mili giây thì báo mất dấu sau 8 giây, hiểu là giây thì hãng không
 *   bao giờ báo — cả hai ca đều an toàn vì app TỰ đếm vắng người (Cai.XOAY_VANG_MS).
 *
 * Quay về hướng cũ dùng turnLeft/turnRight — hai hàm CÓ tài liệu đơn vị (độ, độ/giây).
 * theta của getCurrentPose là RADIAN (09-ban-do-va-vi-tri.md). Chiều dương theta giả định
 * NGƯỢC chiều kim đồng hồ (quy ước bản đồ ROS) → lệch dương = quay trái. Log ghi theta
 * trước/sau để đối chứng trên máy thật.
 */
object DoiDien {

    private const val TAG = "BVDoiDien"

    private var reqId = 7000
    @Synchronized private fun nextReqId(): Int = ++reqId

    @Volatile private var dangBat = false
    /* ⚠ id = 0 LÀ NGƯỜI THẬT. Đo 24/09: người đứng trước robot mang id=0. Bản đầu (chép
       DonKhach của app Hùng Vương) lọc id <= 0 nên robot thấy người mà không bao giờ bám. */
    private const val CHUA_AI = -1
    @Volatile private var aiDo = CHUA_AI        // id người đang được theo
    @Volatile private var dangXoayTheo = false
    @Volatile private var huongGoc: Float? = null
    private var demThay = 0
    private var mocVang = 0L
    private var ungVienTruoc = CHUA_AI
    private var lucGhiCuoi = 0L
    private var daGhiMau = 0

    fun dangHoatDong(): Boolean = dangBat

    /** Đang bám một người đứng trước robot — DiChuyen coi đó là "còn người đang dùng". */
    fun coNguoiTruocMat(): Boolean = dangBat && aiDo != CHUA_AI

    // ───────────────────────── Bật / tắt ─────────────────────────

    /** Người dân vừa rời màn chờ. Gọi lại khi đang bật thì bỏ qua. */
    @Synchronized
    fun bat(giuHuongCu: Boolean = false) {
        if (!Cai.BAT_XOAY_THEO_NGUOI || dangBat) return
        RobotHelper.lyDoChuaSanSang()?.let { Log.w(TAG, "Không bật focus — robot chưa sẵn sàng"); return }
        dangBat = true
        aiDo = CHUA_AI; demThay = 0; mocVang = 0L; ungVienTruoc = CHUA_AI; daGhiMau = 0
        // Bật lại sau khi nhảy: giữ hướng đứng lúc RỜI MÀN CHỜ, không lấy hướng giữa buổi.
        if (!giuHuongCu || huongGoc == null) nhoHuongGoc()
        val ma = runCatching {
            RobotApi.getInstance().startGetAllPersonInfo(nextReqId(), nghe)
        }.getOrElse { e -> Log.w(TAG, "startGetAllPersonInfo ngoại lệ: ${e.message}"); -1 }
        Log.d(TAG, "BẬT focus — tìm người trong ${Cai.XOAY_NGUONG_MET} m · mã=$ma")
        if (ma < 0) dangBat = false
    }

    /** Thôi theo người. veHuong = xoay về hướng đứng lúc rời màn chờ. */
    @Synchronized
    fun tat(veHuong: Boolean) {
        if (!dangBat) return
        dangBat = false
        runCatching { RobotApi.getInstance().stopGetAllPersonInfo(nextReqId(), nghe) }
        thoiXoayTheo("tắt focus")
        aiDo = CHUA_AI; demThay = 0
        Log.d(TAG, "TẮT focus (về hướng cũ = $veHuong)")
        if (veHuong) veHuongGoc()
    }

    // ───────────────────────── Tìm người ─────────────────────────

    private val nghe = object : PersonInfoListener() {
        override fun onResult(status: Int, response: String?) {
            Log.d(TAG, "person onResult status=$status response=$response")
        }

        /** ⚠ Bắn mỗi khung hình — không làm gì nặng ở đây. */
        override fun onData(code: Int, ds: MutableList<Person>?) {
            if (!dangBat) return

            // Ghi vài mẫu đầu để đo đơn vị angle trên máy thật.
            if (daGhiMau < 3 && !ds.isNullOrEmpty()) {
                daGhiMau++
                Log.d(TAG, "MẪU người: " + ds.joinToString(" | ") {
                    "id=${it.id} góc=${it.angle} gócTrongKhung=${"%.1f".format(it.angleInView)} " +
                    "xa=${"%.2f".format(it.distance)}m mặt=${it.isWithFace}"
                })
            }

            // Người GẦN NHẤT trong ngưỡng; có mặt được ưu tiên — người đang nhìn màn hình.
            var chon: Person? = null
            ds?.forEach { p ->
                val d = p.distance
                if (d < 0.05 || d > Cai.XOAY_NGUONG_MET || p.id < 0) return@forEach
                val c = chon
                if (c == null || (p.isWithFace && !c.isWithFace) ||
                    (p.isWithFace == c.isWithFace && d < c.distance)) chon = p
            }

            // Đang theo một người: còn thấy họ thì thôi, vắng lâu thì nhả để tìm người khác.
            // Mỗi 2 giây ghi robot đang thấy ai — để đo trên máy thật.
            val bay = System.currentTimeMillis()
            if (bay - lucGhiCuoi > 2_000) {
                lucGhiCuoi = bay
                Log.d(TAG, "thấy ${ds?.size ?: 0} người: " + (ds ?: emptyList<Person>()).joinToString(" ") {
                    "[id=${it.id} ${it.angle}° ${"%.2f".format(it.distance)}m${if (it.isWithFace) " mặt" else ""}]"
                } + " · đang theo=${if (aiDo == CHUA_AI) "—" else aiDo}")
            }

            if (aiDo != CHUA_AI) {
                val conThay = ds?.any { it.id == aiDo } == true
                if (conThay) { mocVang = 0L; return }
                if (mocVang == 0L) { mocVang = System.currentTimeMillis(); return }
                if (System.currentTimeMillis() - mocVang >= Cai.XOAY_VANG_MS) {
                    Log.d(TAG, "Người id=$aiDo đã rời đi — nhả, tìm người khác")
                    thoiXoayTheo("vắng người"); aiDo = CHUA_AI; mocVang = 0L; demThay = 0
                }
                return
            }

            val p = chon ?: run { demThay = 0; return }
            // Đòi thấy CÙNG một người hai khung liên tiếp — người đi ngang không kéo robot quay.
            if (p.id == ungVienTruoc) demThay++ else { ungVienTruoc = p.id; demThay = 1 }
            if (demThay < 2) return

            aiDo = p.id; demThay = 0
            Log.d(TAG, "THEO người id=${p.id} góc=${p.angle} xa=${"%.2f".format(p.distance)}m mặt=${p.isWithFace}")
            xoayTheo(p.id)
        }
    }

    // ───────────────────────── Xoay theo ─────────────────────────

    private fun xoayTheo(personId: Int) {
        val t0 = System.currentTimeMillis()
        val ma = runCatching {
            RobotApi.getInstance().startFocusFollow(
                nextReqId(), personId, Cai.XOAY_MAT_DAU_MS, Cai.XOAY_NGUONG_MET.toFloat(),
                object : ActionListener() {
                    override fun onStatusUpdate(status: Int, data: String?) {
                        val ms = System.currentTimeMillis() - t0
                        val ten = when (status) {
                            Definition.STATUS_TRACK_TARGET_SUCCEED -> "ĐÃ BÁM ĐƯỢC NGƯỜI"
                            Definition.STATUS_GUEST_LOST -> "hãng báo MẤT DẤU"
                            Definition.STATUS_GUEST_FARAWAY -> "người đã ra xa"
                            Definition.STATUS_GUEST_APPEAR -> "người quay lại gần"
                            else -> "status=$status"
                        }
                        Log.d(TAG, "focus: $ten sau ${ms}ms · $data")
                        if (status == Definition.STATUS_GUEST_LOST && aiDo == personId) {
                            thoiXoayTheo("hãng báo mất dấu"); aiDo = CHUA_AI; mocVang = 0L
                        }
                    }
                    override fun onError(code: Int, msg: String?) {
                        Log.w(TAG, "focus LỖI code=$code msg=$msg")
                        dangXoayTheo = false
                        if (aiDo == personId) aiDo = CHUA_AI  // cho phép thử lại với người kế
                    }
                    override fun onResult(status: Int, response: String?) {
                        Log.d(TAG, "focus kết thúc status=$status $response")
                        dangXoayTheo = false
                    }
                })
        }.getOrElse { e -> Log.w(TAG, "startFocusFollow ngoại lệ: ${e.message}"); -1 }
        Log.d(TAG, "startFocusFollow(id=$personId) trả mã = $ma")
        dangXoayTheo = ma >= 0
        if (ma < 0) aiDo = CHUA_AI
    }

    private fun thoiXoayTheo(viSao: String) {
        if (!dangXoayTheo) return
        dangXoayTheo = false
        runCatching { RobotApi.getInstance().stopFocusFollow(nextReqId()) }
        Log.d(TAG, "stopFocusFollow ($viSao)")
    }

    // ───────────────────────── Hướng đứng ban đầu ─────────────────────────

    private fun nhoHuongGoc() {
        huongGoc = runCatching { RobotApi.getInstance().currentPose?.theta }.getOrNull()
        Log.d(TAG, "Nhớ hướng đứng: theta=$huongGoc rad" +
                   if (huongGoc == null) " (chưa định vị — về màn chờ sẽ KHÔNG xoay lại)" else "")
    }

    private fun veHuongGoc() {
        val goc = huongGoc ?: return
        val nay = runCatching { RobotApi.getInstance().currentPose?.theta }.getOrNull() ?: return
        var lech = Math.toDegrees((goc - nay).toDouble())
        while (lech > 180) lech -= 360
        while (lech < -180) lech += 360
        if (Math.abs(lech) < Cai.XOAY_BO_QUA_DO) { Log.d(TAG, "Lệch %.1f° — khỏi xoay".format(lech)); return }

        val traiQuay = lech > 0
        Log.d(TAG, "Về hướng cũ: theta nay=%.3f gốc=%.3f → quay %s %.1f°"
            .format(nay, goc, if (traiQuay) "TRÁI" else "PHẢI", Math.abs(lech)))
        val nghe = object : CommandListener() {
            override fun onResult(result: Int, message: String?) {
                val sau = runCatching { RobotApi.getInstance().currentPose?.theta }.getOrNull()
                Log.d(TAG, "Quay xong result=$result $message · theta sau=$sau (gốc=$goc)")
            }
        }
        runCatching {
            val api = RobotApi.getInstance()
            if (traiQuay) api.turnLeft(nextReqId(), Cai.XOAY_TOC_DO, Math.abs(lech).toFloat(), nghe)
            else api.turnRight(nextReqId(), Cai.XOAY_TOC_DO, Math.abs(lech).toFloat(), nghe)
        }.onFailure { Log.w(TAG, "turnLeft/Right ngoại lệ: ${it.message}") }
    }
}
