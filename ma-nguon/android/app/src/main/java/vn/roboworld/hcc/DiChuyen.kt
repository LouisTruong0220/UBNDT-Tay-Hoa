package vn.roboworld.hcc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DI CHUYỂN — anh Trường yêu cầu 29/09/2026. Ba việc, MỘT bộ điều phối:
 *
 *   ① VỀ TRẠM SẠC — nút trên màn chính, mật khẩu Cai.MAT_KHAU_VE_SAC.
 *                   Pin dưới Cai.PIN_VE_SAC thì tự về, KHÔNG hỏi mật khẩu.
 *   ② DU HÀNH     — đi vòng Cai.DIEM_DU_HANH liên tục, IM LẶNG. Chạm màn hình là dừng.
 *   ③ VỀ LỄ TÂN   — quá Cai.CHO_VE_LE_TAN_MS không ai tương tác → tự về Cai.DIEM_LE_TAN.
 *
 * Gom một chỗ vì ba việc cùng tranh GẦM MÁY: du hành đang đi thì không được tự về lễ tân,
 * đang về sạc thì không được du hành, pin yếu thì cắt ngang mọi việc. Ba đồng hồ rời rạc
 * ở ba file là sớm muộn có lúc hai lệnh đi chồng lên nhau.
 *
 * ⚠ Chạy ở KOTLIN, không ở lớp web: WebView bị tạm dừng khi app lùi xuống nền, còn pin thì
 *   vẫn cạn. Chép lối app su-kien-rbw (Pin.kt · TuanTra.kt) đã chạy máy thật.
 *
 * TƯƠNG TÁC là gì — chỉ những thứ chắc chắn do NGƯỜI làm:
 *   · chạm / gõ màn hình (lớp web gọi CAU.coTuongTac ở pointerdown)
 *   · nói "Xin chào" ở màn chờ
 *   Thêm hai thứ KHÔNG dừng chuyến đi nhưng đặt lại đồng hồ 60 giây:
 *   · robot đang nói (đọc thủ tục dài cho người đứng nghe)
 *   · camera đang bám một người đứng trước mặt (DoiDien)
 *
 * ⚠ ĐỪNG lấy CAU.nguoiDungThaoTac làm tín hiệu tương tác: lớp web gọi nó trong ve() — cả khi
 *   CHÍNH APP tự chuyển màn (robot về lễ tân kéo màn hình về màn chờ) → chuyến đi vừa bắt
 *   đầu sẽ tự huỷ chính nó.
 */
object DiChuyen {

    private const val TAG = "BVDiChuyen"
    private val h = Handler(Looper.getMainLooper())

    enum class Viec { NGHI, DU_HANH, VE_LE_TAN, VE_SAC }

    @Volatile var viec = Viec.NGHI; private set

    /** Mỗi lần đổi việc tăng một nấc — callback của chuyến đi cũ về muộn thì bỏ. */
    @Volatile private var theHe = 0

    @Volatile var pin = -1; private set
    @Volatile var dangCam = false; private set
    private var daDocPinLanDau = false

    /** Về sạc vì pin yếu → sạc tới Cai.PIN_DI_TIEP mới rời trạm. */
    @Volatile private var sacDoPinYeu = false
    /**
     * Cán bộ bấm về sạc (hoặc app mở lúc robot đang nằm trên cọc) → đứng sạc, KHÔNG tự rời
     * trạm. Gỡ khi có người chạm màn hình: sáng hôm sau cán bộ chạm là robot ra lễ tân.
     * Không có cờ này thì tối cho robot về sạc, 60 giây sau nó tự bỏ cọc ra sảnh đứng.
     */
    @Volatile private var giuTaiSac = false
    /** Đã đứng ở điểm lễ tân — không đi lại chỗ mình đang đứng. Robot đi đâu khác là xoá. */
    @Volatile private var daOLeTan = false

    @Volatile private var lucTuongTac = System.currentTimeMillis()
    /** Lúc robot cắm được vào cọc (hoặc lúc app thấy đang cắm). 0 = chưa. */
    @Volatile private var lucCamSac = 0L
    private var lucThuSacCuoi = 0L
    private var viTriDuHanh = 0
    @Volatile var appDangHien = true

    /** Lớp web muốn biết để hiện nhãn trên màn chờ / màn trạm sạc. */
    private fun baoWeb(trangThai: String, chu: String = "") = Cau.baoDiChuyen(viec.name, trangThai, chu, pin, dangCam)

    // ───────────────────────── Khởi động ─────────────────────────

    fun batDau(ctx: Context) {
        ctx.applicationContext.registerReceiver(ngheP, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        h.postDelayed(canhGac, 10_000)
        Log.d(TAG, "Bắt đầu: về sạc dưới ${Cai.PIN_VE_SAC}% · đi tiếp từ ${Cai.PIN_DI_TIEP}% · " +
                   "du hành ${Cai.DIEM_DU_HANH} · lễ tân '${Cai.DIEM_LE_TAN}' sau ${Cai.CHO_VE_LE_TAN_MS / 1000}s")
    }

    /* ⚠ Đọc pin bằng ACTION_BATTERY_CHANGED của Android, như app su-kien-rbw: đường tiêu chuẩn,
       và cho biết ĐANG CẮM SẠC hay không — thứ quyết định khi nào được rời trạm. */
    private val ngheP = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i == null) return
            val muc = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val tran = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (muc < 0 || tran <= 0) return
            val camTruoc = dangCam
            pin = muc * 100 / tran
            dangCam = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (!daDocPinLanDau) {
                daDocPinLanDau = true
                if (dangCam) { giuTaiSac = true; lucCamSac = System.currentTimeMillis() }  // mở app lúc đang nằm trên cọc: cứ để sạc
                Log.d(TAG, "Pin lúc mở app: $pin% · cắm sạc=$dangCam")
            } else if (camTruoc != dangCam) {
                Log.d(TAG, "Pin $pin% · ${if (dangCam) "ĐÃ CẮM sạc" else "RÚT sạc"}")
                if (dangCam) daOLeTan = false
            }
        }
    }

    // ───────────────────────── Người tương tác ─────────────────────────

    /** Người chạm màn hình / nói "Xin chào". Dừng du hành và chuyến về lễ tân (KHÔNG dừng về sạc). */
    fun coTuongTac() {
        val bay = System.currentTimeMillis()
        lucTuongTac = bay
        /* ⚠ Chỉ gỡ khi đã cắm sạc được HƠN 2 PHÚT: cán bộ vừa cho robot về sạc thường bấm
           "Trang chủ" ngay sau đó — tính cú chạm ấy là "có người dùng" thì 60 giây sau robot
           tự bỏ cọc ra sảnh, đúng cái việc họ vừa muốn tránh. */
        if (giuTaiSac && viec != Viec.VE_SAC && bay - lucCamSac > 120_000) {
            giuTaiSac = false; Log.d(TAG, "Có người dùng máy — thôi giữ robot ở trạm sạc")
        }
        if (viec == Viec.DU_HANH || viec == Viec.VE_LE_TAN) dung("người chạm màn hình")
    }

    /** Robot đang nói / còn người đứng trước mặt: chưa được bỏ đi, nhưng cũng không dừng việc gì. */
    fun coHoatDong() { lucTuongTac = System.currentTimeMillis() }

    /** Dừng việc đang làm (trừ về sạc do pin yếu — cái đó chỉ pin mới gỡ). */
    fun dung(viSao: String) {
        val cu = viec
        if (cu == Viec.NGHI) return
        if (cu == Viec.VE_SAC && sacDoPinYeu) return
        theHe++
        viec = Viec.NGHI
        h.removeCallbacks(buocDuHanh)
        if (cu == Viec.VE_SAC) RobotHelper.dungVeSac() else RobotHelper.dungDieuHuong()
        Log.d(TAG, "DỪNG $cu — $viSao")
        baoWeb("da-dung")
    }

    // ───────────────────────── ② Du hành ─────────────────────────

    /** Trả chuỗi rỗng nếu đã bắt đầu, không thì lý do tiếng Việt để lớp web hiện ra. */
    fun batDuHanh(): String {
        RobotHelper.lyDoChuaSanSang()?.let { return it }
        if (!dangCam && pin in 0 until Cai.PIN_VE_SAC) return "Pin còn $pin%, robot cần về sạc trước."
        if (dangCam) giuTaiSac = false
        dung("chuyển sang du hành")
        theHe++
        viec = Viec.DU_HANH
        daOLeTan = false
        viTriDuHanh = 0
        Log.d(TAG, "BẮT ĐẦU du hành: ${Cai.DIEM_DU_HANH.joinToString(" → ")} → lặp lại")
        baoWeb("bat-dau")
        val the = theHe
        if (dangCam) RobotHelper.roiSac { if (the == theHe) h.post(buocDuHanh) }
        else h.post(buocDuHanh)
        return ""
    }

    /**
     * Một chặng. Một điểm đi không được thì BỎ QUA, đi điểm kế — bài học su-kien-rbw: dừng
     * cả vòng khi gặp lỗi là robot đứng chết ngay phút thứ mười ở chỗ đông người.
     * IM LẶNG hoàn toàn, kể cả lúc lỗi (anh Trường chốt: "trên đường đi không nói gì cả").
     */
    private val buocDuHanh = object : Runnable {
        override fun run() {
            if (viec != Viec.DU_HANH) return
            val the = theHe
            RobotHelper.lyDoChuaSanSang()?.let {
                Log.d(TAG, "Du hành chờ: $it — thử lại sau 10 giây")
                h.postDelayed(this, 10_000); return
            }
            val diem = Cai.DIEM_DU_HANH[viTriDuHanh % Cai.DIEM_DU_HANH.size]
            viTriDuHanh++
            Log.d(TAG, "Du hành → '$diem'")
            baoWeb("dang-di", diem)
            // Một chặng chỉ được báo xong MỘT lần: hãng có thể bắn cả onStatusUpdate lỗi lẫn onError.
            val xong = AtomicBoolean(false)
            RobotHelper.dieuHuongToi(
                diem,
                khiToiNoi = {
                    if (xong.compareAndSet(false, true) && the == theHe) {
                        Log.d(TAG, "✓ tới '$diem', đi tiếp ngay"); h.post(this)
                    }
                },
                khiLoi = { loi ->
                    if (xong.compareAndSet(false, true) && the == theHe) {
                        Log.w(TAG, "✗ không tới được '$diem': $loi — bỏ qua, 3 giây nữa đi điểm kế")
                        h.postDelayed(this, 3_000)
                    }
                },
                khiCapNhat = { }
            )
        }
    }

    // ───────────────────────── ③ Về lễ tân ─────────────────────────

    private fun veLeTan() {
        theHe++
        val the = theHe
        viec = Viec.VE_LE_TAN
        Log.d(TAG, "Bắt đầu về '${Cai.DIEM_LE_TAN}'")
        Cau.veManChoDeDi()          // xoá dấu vết lượt trước, KHÔNG xoay về hướng cũ
        baoWeb("bat-dau")
        val di = {
            if (the == theHe) {
                val xong = AtomicBoolean(false)
                RobotHelper.dieuHuongToi(
                    Cai.DIEM_LE_TAN,
                    khiToiNoi = { if (xong.compareAndSet(false, true) && the == theHe) toiLeTan() },
                    khiLoi = { loi ->
                        if (xong.compareAndSet(false, true) && the == theHe) {
                            if (RobotHelper.laDangODich()) toiLeTan()
                            else {
                                Log.w(TAG, "Về lễ tân không được: $loi — thử lại sau ${Cai.CHO_VE_LE_TAN_MS / 1000}s")
                                viec = Viec.NGHI; lucTuongTac = System.currentTimeMillis()
                                baoWeb("loi", loi)
                            }
                        }
                    },
                    khiCapNhat = { }
                )
            }
        }
        // Chờ lớp web về màn chờ xong (nó gọi tatDoiDien) rồi mới ra lệnh đi.
        if (dangCam) RobotHelper.roiSac { h.postDelayed({ di() }, 500) }
        else h.postDelayed({ di() }, 800)
    }

    /**
     * Nút "Về màn chờ" (anh Trường yêu cầu 29/09/2026): về lễ tân NGAY, không chờ 60 giây.
     * Không đi khi: đang ở lễ tân rồi · đang về lễ tân · đang về sạc · pin yếu (đang sạc dở
     * hoặc dưới ngưỡng). Người bấm có chủ đích nên gỡ luôn cờ giữ-ở-trạm-sạc.
     */
    fun veLeTanTheoNut() = h.post {
        when {
            viec == Viec.VE_SAC || viec == Viec.VE_LE_TAN -> Log.d(TAG, "Nút về màn chờ: đang $viec — bỏ qua")
            sacDoPinYeu || (!dangCam && pin in 0 until Cai.PIN_VE_SAC) -> Log.d(TAG, "Nút về màn chờ: pin yếu ($pin%) — ở yên")
            daOLeTan -> Log.d(TAG, "Nút về màn chờ: đang đứng ở lễ tân rồi")
            RobotHelper.lyDoChuaSanSang() != null -> Log.w(TAG, "Nút về màn chờ: robot chưa sẵn sàng")
            else -> {
                if (viec == Viec.DU_HANH) dung("bấm về màn chờ")
                giuTaiSac = false
                Log.d(TAG, "Nút về màn chờ → về '${Cai.DIEM_LE_TAN}' ngay")
                veLeTan()
            }
        }
    }

    private fun toiLeTan() {
        viec = Viec.NGHI
        daOLeTan = true
        lucTuongTac = System.currentTimeMillis()
        RobotHelper.xoayTheoHuongDiem(Cai.DIEM_LE_TAN)
        Log.d(TAG, "✓ Đã đứng ở '${Cai.DIEM_LE_TAN}'")
        baoWeb("toi-noi")
    }

    // ───────────────────────── ① Về trạm sạc ─────────────────────────

    fun kiemMatKhau(ma: String?): Boolean = ma?.trim() == Cai.MAT_KHAU_VE_SAC

    /** thuCong = cán bộ bấm nút (đã qua mật khẩu); false = pin yếu tự về. */
    fun veSac(thuCong: Boolean) {
        if (viec == Viec.VE_SAC) return
        h.removeCallbacks(buocDuHanh)
        if (viec != Viec.NGHI) RobotHelper.dungDieuHuong()
        theHe++
        val the = theHe
        viec = Viec.VE_SAC
        daOLeTan = false
        lucThuSacCuoi = System.currentTimeMillis()
        if (thuCong) giuTaiSac = true else sacDoPinYeu = true
        Log.w(TAG, "VỀ TRẠM SẠC — ${if (thuCong) "cán bộ bấm (mật khẩu đúng)" else "PIN YẾU $pin%"}")
        baoWeb("bat-dau", if (thuCong) "" else "pin-yeu")
        if (!thuCong) {
            /* Một câu thôi, để người đang đứng trước robot hiểu vì sao nó bỏ đi.
               ⚠ Nói SAU khi lớp web về màn chờ xong: ve() gọi thaoTac() → cắt tiếng, nói trước
               là câu này bị chính lần chuyển màn đó nuốt mất. */
            Cau.veManChoDeDi()
            h.postDelayed({
                if (the == theHe) RobotHelper.doc("Tôi sắp hết pin, xin phép anh chị cho tôi về sạc một lát ạ.") {}
            }, 800)
        }
        // Đợi câu nói / lớp web yên rồi mới đi.
        h.postDelayed({
            if (the != theHe) return@postDelayed
            RobotHelper.veSac { ok, chu ->
                if (the != theHe) return@veSac
                viec = Viec.NGHI
                Log.d(TAG, "Về sạc: ${if (ok) "ĐÃ CẮM" else "KHÔNG được — $chu"}")
                if (ok) lucCamSac = System.currentTimeMillis()
                baoWeb(if (ok) "da-sac" else "loi-sac", chu)
            }
        }, if (thuCong) 300L else 4_000L)
    }

    // ───────────────────────── Canh gác 5 giây/lần ─────────────────────────

    private val canhGac = object : Runnable {
        override fun run() {
            runCatching { xet() }.onFailure { Log.w(TAG, "canh gác lỗi: ${it.message}") }
            h.postDelayed(this, 5_000)
        }
    }

    private fun xet() {
        val bay = System.currentTimeMillis()

        // Pin yếu: cắt mọi việc (trừ đang về sạc), về sạc. Hỏng thì 2 phút thử lại.
        if (!dangCam && pin in 0 until Cai.PIN_VE_SAC && viec != Viec.VE_SAC &&
            bay - lucThuSacCuoi > 120_000 && RobotHelper.lyDoChuaSanSang() == null) {
            NhayMua.dung(false)
            veSac(thuCong = false)
            return
        }
        // Sạc đủ sau lần pin yếu: được rời trạm.
        if (sacDoPinYeu && pin >= Cai.PIN_DI_TIEP) {
            sacDoPinYeu = false
            lucTuongTac = 0L                 // ra lễ tân ngay ở lượt xét sau
            Log.d(TAG, "Pin $pin% — sạc đủ, sẽ ra '${Cai.DIEM_LE_TAN}'")
        }

        if (viec != Viec.NGHI || !appDangHien) return
        if (sacDoPinYeu || giuTaiSac || daOLeTan) return
        if (NhayMua.dangHoatDong() || MainApplication.micDangMo()) { lucTuongTac = bay; return }
        if (DoiDien.coNguoiTruocMat()) { lucTuongTac = bay; return }
        if (bay - lucTuongTac < Cai.CHO_VE_LE_TAN_MS) return
        if (RobotHelper.lyDoChuaSanSang() != null) return
        veLeTan()
    }

    /** App lùi xuống nền: RobotOS thu quyền điều khiển — dừng mọi chuyến (trừ về sạc pin yếu). */
    fun khiRoiApp() {
        appDangHien = false
        dung("app lùi xuống nền")
    }

    fun khiVeApp() {
        appDangHien = true
        lucTuongTac = System.currentTimeMillis()
    }

    fun tomTat(): String = "việc=$viec · pin=$pin% · cắm=$dangCam · pinYếu=$sacDoPinYeu · " +
        "giữSạc=$giuTaiSac · ởLễTân=$daOLeTan · vắng=${(System.currentTimeMillis() - lucTuongTac) / 1000}s"
}
