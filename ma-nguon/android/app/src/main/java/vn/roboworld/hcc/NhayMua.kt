package vn.roboworld.hcc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.CommandListener

/**
 * NHẢY MÚA — mục Giải trí, anh Trường yêu cầu 24/09/2026.
 *
 * ── AI GIỮ NHỊP ──────────────────────────────────────────────────────────────
 * LỚP WEB, không phải Kotlin. Lớp web đọc audio.currentTime của bài nhạc rồi bắn từng động
 * tác đúng thời điểm phách đã đo sẵn (khối VU_DAO trong khung-app.html, sinh bằng
 * tools/do-nhip-nhac.py). Bản đầu để Kotlin tự đếm 492 ms/phách từ lúc bấm nút — nhạc nạp
 * chậm nửa giây là cả bài lệch phách, vì hai đồng hồ không biết nhau.
 * Kotlin ở đây chỉ: nhả gầm máy · thi hành từng động tác · lưới an toàn · trả hướng cũ.
 *
 * ── ĐỘNG TÁC — chỉ dùng lệnh CÓ tài liệu đơn vị (08-chuyen-dong-co-ban.md) ─────────
 *   "L<độ>" / "R<độ>"  turnLeft / turnRight ở tốc độ trần 50°/s — đo 12° mất 0,97–1,3 s
 *   "U" / "D"          moveHead dọc tương đối ±GOC_GAT (Nova chỉ gật dọc được)
 * startPlayAction(GongFuBean) của hãng KHÔNG có tài liệu — không dùng.
 *
 * ⚠ Lắc thân CHIẾM GẦM MÁY như focus follow → tắt DoiDien trước, nhảy xong bật lại.
 * ⚠ Mỗi ô nhịp lớp web gửi động tác ĐỐI XỨNG (tổng 0°). Vẫn có thể trôi vài độ nếu một
 *   lệnh bị hãng từ chối — nên lúc dừng, nếu robot đã định vị, xoay về đúng hướng đầu bài.
 */
object NhayMua {

    private const val TAG = "BVNhayMua"
    private val h = Handler(Looper.getMainLooper())

    private const val GOC_GAT = 10
    /* ⚠ Tốc độ ghi trong lệnh KHÔNG quyết định thời gian: đo 24/09, 18° xin 45°/s mà mất ~1,0 s
       (tăng tốc + hãm). Cứ xin trần 50°/s của hãng cho nhanh nhất; nhịp do lớp web lo. */
    private const val TOC_DO_XOAY = 50f
    /** Lớp web im quá lâu (trang treo, nhạc hỏng) → tự dừng, không để robot đứng giữa điệu. */
    private const val IM_TOI_DA_MS = 3_000L

    private var reqId = 8000
    @Synchronized private fun nextReqId(): Int = ++reqId

    @Volatile private var dangNhay = false
    @Volatile private var lucLenhCuoi = 0L
    private var lucHet = 0L
    private var huongDau: Float? = null
    @Volatile private var soLenh = 0
    @Volatile private var soTuChoi = 0

    fun dangHoatDong(): Boolean = dangNhay

    /** Vào điệu. `giay` = độ dài bài nhạc — trần cứng, quá là tự dừng. */
    @Synchronized
    fun bat(giay: Int) {
        if (dangNhay) return
        RobotHelper.lyDoChuaSanSang()?.let { Log.w(TAG, "Không nhảy — robot chưa sẵn sàng"); return }
        DoiDien.tat(false)                 // nhả gầm máy
        huongDau = runCatching { RobotApi.getInstance().currentPose?.theta }.getOrNull()
        dangNhay = true
        soLenh = 0; soTuChoi = 0
        lucLenhCuoi = System.currentTimeMillis()
        lucHet = lucLenhCuoi + (giay + 3) * 1000L
        Log.d(TAG, "BẮT ĐẦU nhảy ${giay}s · hướng đầu bài theta=$huongDau")
        h.postDelayed(canhGac, 1_000)
    }

    /** Một động tác — lớp web gọi đúng phách. */
    fun dongTac(ma: String) {
        if (!dangNhay) return
        lucLenhCuoi = System.currentTimeMillis()
        val api = RobotApi.getInstance()
        soLenh++
        runCatching {
            when {
                ma == "U" -> api.moveHead(nextReqId(), "relative", "relative", 0, GOC_GAT, nghe(ma))
                ma == "D" -> api.moveHead(nextReqId(), "relative", "relative", 0, -GOC_GAT, nghe(ma))
                ma.length > 1 && (ma[0] == 'L' || ma[0] == 'R') -> {
                    val goc = ma.substring(1).toFloatOrNull()?.coerceIn(3f, 30f) ?: return
                    if (ma[0] == 'L') api.turnLeft(nextReqId(), TOC_DO_XOAY, goc, nghe(ma))
                    else api.turnRight(nextReqId(), TOC_DO_XOAY, goc, nghe(ma))
                }
                else -> Unit
            }
        }.onFailure { Log.w(TAG, "động tác $ma lỗi: ${it.message}") }
    }

    /** Dừng. batLaiDoiDien = quay lại chế độ xoay theo người (khi chưa về màn chờ). */
    @Synchronized
    fun dung(batLaiDoiDien: Boolean) {
        if (!dangNhay) return
        dangNhay = false
        h.removeCallbacks(canhGac)
        val api = RobotApi.getInstance()
        runCatching { api.stopMove(nextReqId(), nghe("stopMove")) }
        runCatching { api.resetHead(nextReqId(), nghe("resetHead")) }
        Log.d(TAG, "DỪNG nhảy — $soLenh động tác, $soTuChoi bị hãng từ chối")
        // Chờ thân đứng yên rồi mới đo lệch hướng; trả hướng xong mới bật lại xoay theo người.
        h.postDelayed({
            traHuongDau()
            if (batLaiDoiDien) h.postDelayed({ DoiDien.bat(giuHuongCu = true) }, 1_500)
        }, 700)
    }

    private val canhGac = object : Runnable {
        override fun run() {
            if (!dangNhay) return
            val bay = System.currentTimeMillis()
            if (bay - lucLenhCuoi > IM_TOI_DA_MS) {
                Log.w(TAG, "Lớp web im ${bay - lucLenhCuoi}ms — tự dừng"); dung(false); return
            }
            if (bay > lucHet) { Log.w(TAG, "Quá độ dài bài — tự dừng"); dung(false); return }
            h.postDelayed(this, 1_000)
        }
    }

    private fun traHuongDau() {
        val dau = huongDau ?: return
        val nay = runCatching { RobotApi.getInstance().currentPose?.theta }.getOrNull() ?: return
        var lech = Math.toDegrees((dau - nay).toDouble())
        while (lech > 180) lech -= 360
        while (lech < -180) lech += 360
        Log.d(TAG, "Sau bài nhảy lệch %.1f° so với hướng đầu bài".format(lech))
        if (Math.abs(lech) < 5) return
        val api = RobotApi.getInstance()
        runCatching {
            if (lech > 0) api.turnLeft(nextReqId(), 25f, Math.abs(lech).toFloat(), nghe("trả hướng"))
            else api.turnRight(nextReqId(), 25f, Math.abs(lech).toFloat(), nghe("trả hướng"))
        }
    }

    /** Ghi kết quả vài lệnh đầu để đo trên máy thật; đếm mọi lần bị từ chối. */
    private fun nghe(ten: String) = object : CommandListener() {
        override fun onResult(result: Int, message: String?) {
            val tuChoi = message?.contains("fail", true) == true || result < 0
            if (tuChoi) soTuChoi++
            if (soLenh <= 12 || tuChoi || ten.length > 3)
                Log.d(TAG, "$ten → result=$result ${message?.take(80)}")
        }
    }
}
