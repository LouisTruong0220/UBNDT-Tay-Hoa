package vn.roboworld.hcc

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * Màn hình duy nhất của app: một WebView chạy toàn bộ giao diện.
 *
 * Giao diện là khung-app.html — chính file đã chạy trên máy tính, không sửa gì — ghép với
 * dữ liệu thủ tục ở app-data.json. Cả hai đọc THẺ NHỚ TRƯỚC, bản trong APK sau, nên cán bộ
 * trung tâm cập nhật thủ tục chỉ cần đẩy file, không phải build lại app. Xem napGiaoDien().
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    /**
     * Khung giao diện và dữ liệu đang chạy lấy từ đâu — hiện trong bảng tự chẩn đoán.
     * Hai thứ này đến từ hai nơi độc lập, nên theo dõi riêng: nhìn màn robot là biết
     * bản cập nhật vừa đẩy đã ăn chưa, khỏi phải đoán qua ảnh chụp gửi từ xa.
     */
    private var nguonKhung = "APK"
    private var nguonDuLieu = "APK"

    companion object {
        private const val TAG = "HccWeb"

        /** Mốc trong khung-app.html để Kotlin chèn dữ liệu vào. */
        private const val MOC_DU_LIEU = "/*__DU_LIEU__*/"
        private const val MOC_THONG_TIN = "/*__THONG_TIN__*/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)
        anThanhHeThong()

        WebView.setWebContentsDebuggingEnabled(true)   // để soi lỗi bằng chrome://inspect

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Toàn bộ dữ liệu nằm trong file, không gọi mạng — chặn hẳn cho chắc
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = true
            textZoom = 100                              // không để cỡ chữ hệ thống làm vỡ bố cục
            mediaPlaybackRequiresUserGesture = false

            /*
             * ⚠ BA DÒNG DƯỚI ĐÂY QUYẾT ĐỊNH GIAO DIỆN CÓ VỠ HAY KHÔNG.
             *
             * Màn hình robot là 1920×1080 nhưng ở mật độ 560 dpi, nên WebView chỉ cho
             * trang một khung 548×308 CSS px. Bố cục này thiết kế theo tỉ lệ của khung
             * 1920×1080 nên cỡ chữ gốc tính ra chỉ 4,56 px — mà Chrome có SÀN cỡ chữ
             * tối thiểu 8 px, nó tự nâng lên 8. Mọi thứ to lên 1,75 lần, chữ tràn ra
             * ngoài thẻ, nhãn "20 thủ tục" bị cắt mất.
             *
             * Cách chữa: bảo WebView bố cục ở đúng 1920 px rồi thu nhỏ cả trang cho vừa
             * màn hình (meta viewport width=1920 trong index.html). Khi đó cỡ chữ gốc
             * là 16 px, nằm trên sàn, hiển thị y hệt bản chạy trên máy tính.
             * Hai dòng minimumFontSize là lớp chặn thứ hai, phòng khi meta bị bỏ qua.
             */
            useWideViewPort = true          // tôn trọng meta viewport width=1920
            loadWithOverviewMode = true     // thu nhỏ cả trang cho vừa bề ngang màn hình
            minimumFontSize = 1
            minimumLogicalFontSize = 1
        }

        // In lỗi JavaScript ra logcat — WebView trên máy Nova có thể cũ hơn trên máy tính,
        // cú pháp JS đời mới sẽ lỗi ở đây chứ không lỗi lúc build.
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d(TAG, "${m.messageLevel()} ${m.message()} @${m.lineNumber()}")
                return true
            }
        }

        Cau.gan(web)
        web.addJavascriptInterface(Cau, "CAU")
        napGiaoDien()

        xinQuyenMicro()

        Log.d(TAG, "WebView: " + (Build.VERSION.SDK_INT.toString()) + " / " +
                (WebView.getCurrentWebViewPackage()?.versionName ?: "không đọc được"))
    }

    /**
     * Nạp giao diện — THẺ NHỚ TRƯỚC, BẢN TRONG APK SAU.
     *
     * Vì sao phải làm vậy: cán bộ trung tâm sẽ tự cập nhật thủ tục bằng webapp trên máy
     * tính. Nếu dữ liệu nằm chết trong APK thì mỗi lần sửa một chữ phải build lại cả gói
     * rồi cài lại — không ai làm nổi. Đọc từ thẻ nhớ trước thì vòng cập nhật rút xuống
     * còn một lệnh `adb push` và mở lại app.
     *
     * Khung giao diện và dữ liệu để RỜI NHAU, ghép ở đây:
     *   khung  ← files/web/khung-app.html      hoặc assets/khung-app.html
     *   dữ liệu ← files/du-lieu/app-data.json  hoặc assets/app-data.json
     *
     * ⚠ Phải ghép bên Kotlin chứ KHÔNG để HTML tự fetch() file JSON: Chromium chặn
     *   fetch() qua file://, trang sẽ trắng mà không báo lỗi gì.
     *
     * ⚠ baseURL phải là file:///android_asset/ để video biểu cảm (đường dẫn tương đối
     *   bieu-cam/…) vẫn tìm thấy trong APK, kể cả khi khung được đọc từ thẻ nhớ.
     */
    private fun napGiaoDien() {
        try {
            val khung = docUuTien("web/khung-app.html", "khung-app.html") { nguonKhung = it }
            val duLieu = docUuTien("du-lieu/app-data.json", "app-data.json") { nguonDuLieu = it }

            // </ trong chuỗi JSON sẽ cắt sớm thẻ <script> — phải rào lại
            val html = khung.replace(
                MOC_DU_LIEU,
                "window.DU_LIEU=" + duLieu.replace("</", "<\\/") + ";"
            ).replace(MOC_THONG_TIN, thongTinChoWeb())
            if (!html.contains("window.DU_LIEU")) {
                Log.w(TAG, "Khung giao diện KHÔNG có mốc $MOC_DU_LIEU — dữ liệu chưa được ghép!")
            }
            web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
            Log.d(TAG, "Đã nạp giao diện — khung: $nguonKhung (${khung.length} ký tự) · " +
                    "dữ liệu: $nguonDuLieu (${duLieu.length} ký tự)")
        } catch (e: Exception) {
            /*
             * Dữ liệu trên thẻ nhớ hỏng (đẩy dở, JSON sai) thì rơi về bản đóng trong APK.
             * Tuyệt đối không để robot đứng màn trắng trước mặt người dân.
             */
            Log.e(TAG, "Nạp giao diện hỏng — quay về bản trong APK", e)
            nguonKhung = "APK (bản cập nhật bị lỗi)"
            nguonDuLieu = "APK (bản cập nhật bị lỗi)"
            web.loadUrl("file:///android_asset/index.html")
        }
    }

    /**
     * Màn "Thông tin Trung tâm" (24/09/2026): cán bộ + ảnh/video giới thiệu.
     * Đọc files/thong-tin/thong-tin.json trên thẻ nhớ — Trung tâm gửi ảnh là đẩy lên, không
     * phải build lại APK. Thẻ nhớ không có / JSON hỏng → dùng bản trong APK (từ 29/09/2026,
     * anh Trường gửi danh sách chính thức); APK cũng không có → màn hình nói "đang cập nhật".
     * Khuôn tệp: du-lieu/thong-tin-mau.json.
     */
    private fun thongTinChoWeb(): String {
        val thuMuc = java.io.File(getExternalFilesDir(null), "thong-tin")
        val tep = java.io.File(thuMuc, "thong-tin.json")
        val json = runCatching {
            if (tep.isFile) org.json.JSONObject(tep.readText(Charsets.UTF_8)).toString() else null
        }.onFailure { Log.w(TAG, "thong-tin.json trên thẻ nhớ hỏng — dùng bản trong APK", it) }
         .getOrNull()
        if (json != null) {
            Log.d(TAG, "Thông tin Trung tâm: thẻ nhớ (${json.length} ký tự)")
            return "window.THONG_TIN=" + json.replace("</", "<\\/") + ";" +
                   "window.THU_MUC_THONG_TIN=" +
                   org.json.JSONObject.quote("file://" + thuMuc.absolutePath + "/") + ";"
        }
        /* Bản trong APK (29/09/2026): danh sách cán bộ theo Thông báo 16/TB-PVHCC, dựng bằng
           tools/dung-thong-tin.py. Ảnh ở assets/thong-tin/, trỏ bằng file:///android_asset/. */
        val apk = runCatching {
            assets.open("thong-tin/thong-tin.json").bufferedReader().use { org.json.JSONObject(it.readText()).toString() }
        }.getOrNull()
        Log.d(TAG, "Thông tin Trung tâm: " + if (apk == null) "chưa có tệp" else "APK (${apk.length} ký tự)")
        return "window.THONG_TIN=" + (apk?.replace("</", "<\\/") ?: "null") + ";" +
               "window.THU_MUC_THONG_TIN=\"file:///android_asset/thong-tin/\";"
    }

    /** Đọc file: có trên thẻ nhớ thì lấy bản đó, không thì lấy bản đóng trong APK. */
    private fun docUuTien(
        duongNgoai: String,
        duongTrongApk: String,
        ghiNguon: (String) -> Unit
    ): String {
        val ngoai = java.io.File(getExternalFilesDir(null), duongNgoai)
        if (ngoai.isFile && ngoai.length() > 0) {
            ghiNguon("thẻ nhớ")
            return ngoai.readText(Charsets.UTF_8)
        }
        ghiNguon("APK")
        return assets.open(duongTrongApk).bufferedReader().use { it.readText() }
    }

    /** Cho bảng tự chẩn đoán bên lớp web biết dữ liệu đang chạy lấy từ đâu. */
    fun nguonNoiDung(): String = "khung: $nguonKhung · dữ liệu: $nguonDuLieu"

    /**
     * Xin quyền thu âm LÚC CHẠY.
     *
     * ⚠ Khai <uses-permission RECORD_AUDIO> trong Manifest là CHƯA ĐỦ. Từ Android 6 trở đi
     *   quyền này phải xin lúc chạy. Không gọi hàm này thì `dumpsys package` hiện
     *   RECORD_AUDIO không có "granted=true", robot KHÔNG nghe được người dân nói —
     *   mà app không hề báo lỗi gì, thanh trạng thái chỉ hiện nút "Bật microphone".
     */
    private fun xinQuyenMicro() {
        val q = android.Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(q) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Chưa có quyền micro — đang xin")
            requestPermissions(arrayOf(q), 1001)
        } else {
            Log.d(TAG, "Đã có quyền micro")
            MainApplication.tatMicro()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val duoc = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Kết quả xin quyền micro: $duoc")
            /*
             * Có quyền rồi vẫn TẮT mic — không phải quên.
             * App tự bật mic khi vào màn Trò chuyện và tắt lại khi ra. Để mic mở suốt thì
             * robot đứng ở sảnh sẽ xen vào mọi câu chuyện của người đang ngồi chờ, và lúc
             * dẫn đường thì phải im. Muốn robot nghe mọi lúc thì đổi dòng dưới thành
             * batMicro() — chỉ một chỗ này thôi.
             */
            if (duoc) MainApplication.tatMicro()
        }
    }

    /** Ra khỏi tiền cảnh thì đóng mic lại, khỏi nghe lén phòng chờ. */
    override fun onPause() {
        super.onPause()
        MainApplication.tatMicro()
        NhayMua.dung(false)
        DoiDien.tat(false)          // focus follow chiếm gầm máy — ra nền là phải nhả
        DiChuyen.khiRoiApp()        // du hành / về lễ tân dừng lại (về sạc vì pin yếu thì không)
    }

    override fun onResume() {
        super.onResume()
        DiChuyen.khiVeApp()
    }

    /** Chạy toàn màn hình — robot không có thanh trạng thái để người dân bấm nhầm. */
    private fun anThanhHeThong() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) anThanhHeThong()
    }

    /**
     * Nút Back của hệ thống: lùi trong trang trước, hết mới thoát app.
     * Lớp web tự quyết định lùi về đâu (xem window.luiHeThong) — chỉ khi nó đang
     * ở màn chờ biểu cảm mới trả '0' cho phép thoát ra RobotOS Home.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.luiHeThong ? window.luiHeThong() : '0'") { kq ->
            if (kq.contains("0")) super.onBackPressed()
        }
    }

    override fun onDestroy() {
        Cau.go()
        RobotHelper.dungDoc()
        web.destroy()
        super.onDestroy()
    }
}
