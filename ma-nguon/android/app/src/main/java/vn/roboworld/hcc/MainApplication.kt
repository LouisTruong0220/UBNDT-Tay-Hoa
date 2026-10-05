package vn.roboworld.hcc

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.agent.AppAgent
import com.ainirobot.agent.LLMCallback
import com.ainirobot.agent.OnTranscribeListener
import com.ainirobot.agent.action.Action
import com.ainirobot.agent.action.Actions
import com.ainirobot.agent.action.ActionExecutor
import com.ainirobot.agent.base.ActionResult
import com.ainirobot.agent.base.ActionStatus
import com.ainirobot.agent.base.Parameter
import com.ainirobot.agent.base.ParameterType
import com.ainirobot.agent.assit.LLMResponse
import com.ainirobot.agent.base.Transcription
import com.ainirobot.agent.base.llm.LLMConfig
import com.ainirobot.agent.base.llm.LLMMessage
import com.ainirobot.agent.base.llm.Role

/**
 * Điểm khởi động của app lễ tân bệnh viện.
 *
 * Phân vai giữa hai tầng — chỗ dễ hiểu nhầm nhất:
 *
 *   • RobotApi / SkillApi  → dẫn đường và đọc thành tiếng. Đây là phần app sống bằng,
 *                            không phụ thuộc Agent SDK.
 *   • Agent SDK 0.4.7      → nghe (ASR) và gọi mô hình ngôn ngữ. App KHÔNG để AgentOS
 *                            tự hoạch định — xem `isDisablePlan` bên dưới và TraLoi.kt.
 *
 * Toàn bộ phần AI ở đây chép lối làm đã chạy được trên app tra cứu thủ tục Mông Dương
 * (11/08/2026). Bốn thứ dưới đây từng làm mất nhiều ngày ở app kia, đừng đổi nếu chưa
 * đọc kỹ chú thích tại chỗ:
 *   ① SDK phải lấy từ gói offline, không lấy JitPack   (xem app/build.gradle.kts)
 *   ② `businessInfo` phải là null, không được là ""     (xem hoiMoHinh)
 *   ③ bốn công tắc của hãng đều mặc định SAI với app này (xem onCreate)
 *   ④ việc an toàn chặn bằng MÃ, không chặn bằng prompt  (xem ba hàm chan…NeuCan)
 *
 * LƯU Ý: mỗi app CHỈ ĐƯỢC CÓ MỘT thực thể AppAgent.
 */
class MainApplication : Application() {

    companion object {
        private const val TAG = "BVApp"

        @Volatile private var agent: AppAgent? = null

        /** Bối cảnh ứng dụng — TraLoi cần để ghi nhật ký ra thẻ nhớ. */
        @Volatile var boiCanh: android.content.Context? = null; private set

        /**
         * AI đám mây đã trả lời thật hay chưa. Lớp web đọc cờ này để quyết định có hiện
         * nút micro không.
         *
         * ⚠ KHÔNG suy ra từ "agent != null". Dựng được AppAgent chỉ nghĩa là hàm khởi tạo
         *   không ném lỗi — nó vẫn dựng thành công khi dịch vụ của hãng không bind được.
         *   Cờ này chỉ bật khi mô hình trả lời thật, hoặc khi nghe được một câu ASR thật.
         *   Một nút micro bấm vào không phản ứng, trước mặt lãnh đạo bệnh viện, tệ hơn
         *   nhiều so với việc không có nút.
         */
        @Volatile private var agentSanSang = false

        /**
         * MIC ĐANG MỞ HAY KHÔNG — cờ của app, không hỏi SDK.
         *
         * ⚠ Không suy ra từ `AgentCore.isMicrophoneMuted`: app không đụng vào công tắc đó
         *   nữa (bẫy một chiều, xem AppAgent.onCreate). Và cờ này là thứ chặn ở
         *   `onASRResult` — ngoài lúc người bệnh bấm nút micro thì mọi câu nghe được đều
         *   bị bỏ, kể cả khi đang đứng ở màn Trò chuyện.
         */
        @Volatile private var dangChoNghe = false

        /* ── CHẨN ĐOÁN — để app tự khai ra được vì sao mic chết ──────────────────
         * Thêm 11/09/2026: anh Trường báo vào app thì mất nút mic, đợi một lúc mới
         * hiện, bấm nói thì robot không nhận. Không cắm được máy để đọc log, nên app
         * phải tự trả lời được bốn câu: mạng gì · micro của máy bật hay tắt · AI hỏng
         * vì lỗi gì · lần cuối nghe được chữ là bao giờ. Xem thongTinAI(). */
        @Volatile private var loiAICuoi = ""
        @Volatile private var lanThamDo = 0
        @Volatile private var lucNgheCuoi = 0L
        @Volatile private var lucAIThongCuoi = 0L
        @Volatile private var lucMoMic = 0L

        /* ═══════════════════════════════════════════════════════════════
           LƯỢT HỎI — anh Trường chốt 08/09/2026

           > "Người hỏi 1 → Robot trả lời 1. Và lời thoại của AI chỉ phát khi
           >  người dùng không thao tác: quay lại, thoát ra, xoá đoạn chat, hay
           >  bấm mic để thu tiếp thì phải dừng ngay lời đang phát."

           Hai cờ, mỗi cờ bịt một lỗ khác nhau — thiếu một là hở:

           · nhanCauTraLoi — lượt hỏi này CÒN HIỆU LỰC không.
             Tầng hội thoại chạy ở luồng phụ: người bệnh hỏi xong, mô hình trả lời
             về sau vài giây, mà trong mấy giây đó họ đã bấm Quay lại và đang đọc
             thứ khác trên màn hình. Không có cờ này thì robot vẫn cất tiếng đọc
             câu của lượt cũ đè lên việc họ đang làm. Cắt tiếng KHÔNG đủ — câu về
             muộn là câu CHƯA phát, không có gì để cắt.

           · daGuiTrongPhien — một lần bấm mic chỉ gửi ĐÚNG MỘT câu.
             Bộ nhận dạng bắn `final` ở mỗi lần ngắt hơi. Người bệnh già nói chậm,
             ngắt giữa chừng lâu hơn đồng hồ im lặng, thế là câu bị xé làm đôi và
             robot trả lời hai lần rời rạc. Cờ này chốt cứng: mở mic lần nào thì
             gửi được đúng một câu, phần nghe thêm sau đó bỏ.
           ═══════════════════════════════════════════════════════════════ */
        @Volatile private var nhanCauTraLoi = false
        @Volatile private var daGuiTrongPhien = false

        /** Người bệnh vừa hỏi một câu — từ giờ nhận câu trả lời cho lượt này. */
        fun batDauLuotMoi() { nhanCauTraLoi = true }

        /**
         * NGƯỜI BỆNH VỪA THAO TÁC — bỏ lượt hỏi đang chờ, và cắt tiếng ngay.
         *
         * Lớp web gọi hàm này ở mọi cửa: nút Quay lại · Thoát · Xoá đoạn chat ·
         * bấm micro nói tiếp · rời màn Trò chuyện. Cắt cả HAI đường tiếng
         * (SkillApi của app và AgentCore của hãng) — thiếu một đường là robot vẫn
         * còn nói bằng đường kia.
         */
        fun nguoiDungThaoTac() {
            val coGiDeBo = nhanCauTraLoi
            nhanCauTraLoi = false
            RobotHelper.dungDoc()
            runCatching { AgentCore.stopTTS() }
            if (coGiDeBo) Log.d(TAG, "Người bệnh thao tác — bỏ lượt hỏi đang chờ, cắt tiếng")
        }

        /** Tầng hội thoại có được mở miệng lúc này không — chốt CỬA RA của mọi lời nói. */
        fun choPhepNoi(): Boolean = nhanCauTraLoi

        /* ⚠⚠ QUÊN VẾ Ở `batMicro`, TUYỆT ĐỐI KHÔNG Ở `tatMicro`.
         *
         *   Đường gửi câu đi là: dứt câu → hẹn 1 giây → tatMicro() → guiCauDaGom().
         *   Để `quenVe()` trong tatMicro thì nó xoá sạch vế vừa gom NGAY TRƯỚC khi
         *   guiCauDaGom đọc tới — hàm này lấy được chuỗi rỗng, `TraLoi.hoi` không bao
         *   giờ được gọi, và robot đứng im sau khi nghe xong.
         *
         *   Nhìn từ ngoài y hệt "AI hỏng": mic thu tốt, chữ hiện lên màn hình đầy đủ,
         *   nút micro tắt đúng lúc — chỉ là không có câu trả lời nào. Đã dính thật trên
         *   máy 08/09/2026 ở bản v1.5.
         *
         *   Quên ở batMicro thì đúng ngữ nghĩa hơn: bắt đầu một lượt nói mới là bỏ vế
         *   dở của lượt trước. Vế dở khi người bệnh rời màn giữa chừng cũng không sao —
         *   lúc đó không ai gọi guiCauDaGom, và lần bấm mic sau sẽ xoá nó. */
        fun batMicro() {
            moLuotAgentOS()              // đầu lượt mới — mở cửa sổ cho AgentOS
            huyHenTatMic(); quenVe()
            lucMoMic = System.currentTimeMillis()
            daGuiTrongPhien = false      // phiên mới — lại được gửi đúng một câu
            dangChoNghe = true
            datMicro(false)
            henTatMicNeuKhongAiNoi()
        }
        fun tatMicro() { huyHenTatMic(); dangChoNghe = false; datMicro(true) }

        /* ═══════════════════════════════════════════════════════════════
           MICRO BẤM-MỚI-NGHE — chép lối đã chạy thật ở app Medinova (v1.6)
           và app sự kiện Tây Ninh.

           Vì sao: mic tự mở là robot vừa dứt lời đã dỏng tai hứng tạp âm, và
           hai người đứng cạnh nói chuyện riêng cũng bị tính là câu hỏi. Ở sảnh
           bệnh viện còn tệ hơn — đó là chuyện bệnh tình của người đang ngồi chờ.

           Nay: bấm nút micro thì mới thu. Nói xong cứ dừng, một giây sau mic tự
           tắt và câu được gửi đi. Muốn nói tiếp thì bấm lại.

           BA ĐỒNG HỒ, cả ba nằm bên Kotlin vì chỉ Kotlin thấy cờ `final`:
             · 250 ms (hoặc 1,4 giây nếu robot đang nói) — từ lúc bấm tới lúc mở
               mic thật. Nằm ở moMic() bên khung-app.html.
             · 1 giây sau mỗi `final` — dứt câu thì tắt mic và gửi câu đi.
             · 15 giây trần cứng — bấm mic rồi bỏ đi thì tự tắt.
           ═══════════════════════════════════════════════════════════════ */

        /* ── GOM VẾ ──
           Bộ nhận dạng của hãng bắn `final` ở MỖI lần khách ngắt hơi, không phải chỉ ở
           cuối câu. Gửi ngay từng vế thì người bệnh nói "cho tôi hỏi… khoa nhi ở đâu"
           là robot trả lời HAI LẦN cho hai nửa câu, mà nửa đầu tra ra khoa sai.
           Gom lại, chờ một giây im lặng rồi mới gửi cả câu cho TraLoi. */
        private val ve = StringBuilder()

        @Synchronized
        fun gomVe(chu: String) {
            if (chu.isBlank()) return
            if (ve.isNotEmpty()) ve.append(' ')
            ve.append(chu.trim())
        }

        @Synchronized
        fun quenVe() { ve.setLength(0) }

        /** Hết một giây im lặng — gửi cả câu đã gom cho tầng hội thoại. */
        fun guiCauDaGom() {
            val cau = synchronized(this) {
                val t = ve.toString().trim(); ve.setLength(0); t
            }
            if (daGuiTrongPhien) {
                /* MỘT PHIÊN MIC = MỘT CÂU. Tới đây lần thứ hai nghĩa là bộ nhận dạng
                   còn bắn thêm vế sau khi câu đã đi — gửi tiếp là robot trả lời hai
                   lần rời rạc cho cùng một lần bấm nút. */
                Log.d(TAG, "Phiên này đã gửi một câu rồi — bỏ phần nghe thêm: ${cau.take(40)}")
                return
            }
            if (cau.isNotBlank()) {
                daGuiTrongPhien = true
                batDauLuotMoi()          // từ giờ mới nhận câu trả lời cho lượt này
                if (Cai.CHE_DO_AGENTOS) {
                    /* Cửa sổ 30 giây tính lại TỪ LÚC GỬI (sửa 29/09). Trước đây chỉ tính từ lúc
                       bấm mic: nói 10 giây + AgentOS chậm là câu trả lời về sau mốc 30 giây,
                       bị coi là "nghe lỏm" và cắt mất, màn hình đứng ở "Để tôi xem…". */
                    moLuotAgentOS()
                    if (chanTruocAgentOS(cau)) Log.d(TAG, "Chế độ AgentOS — app đã chặn: $cau")
                    else { Log.d(TAG, "Chế độ AgentOS — để AgentOS xử lý câu: $cau"); henDuongLuiAgentOS(cau) }
                } else {
                    Log.d(TAG, "Gửi câu đã gom cho tầng hội thoại: $cau")
                    TraLoi.hoi(cau)
                }
            } else {
                /* Tới đây mà rỗng là có kẻ xoá vế trước khi kịp gửi. Ghi rõ chứ đừng
                   im: nhìn từ ngoài, "nghe xong rồi đứng im" trông y hệt AI hỏng, và
                   không có dòng log này thì đi tìm nhầm sang tận đám mây của hãng. */
                Log.w(TAG, "guiCauDaGom: KHÔNG CÒN VẾ NÀO để gửi — ai đó đã gọi quenVe() " +
                           "giữa lúc tắt mic và lúc gửi. Robot sẽ đứng im.")
            }
        }

        /* ⚠ 1,8 giây chứ không phải 1,0.
           Đo ở bệnh viện: người già kể bệnh ngắt giữa chừng lâu hơn nhiều so với
           khách hội chợ — "cho tôi hỏi… (nghĩ) … khoa nhi ở đâu". Một giây là xé
           câu làm đôi. Đổi lại người nói xong phải chờ thêm nửa giây, đó là cái
           giá rẻ hơn nhiều so với một câu trả lời trật. */
        private const val CHO_DUT_CAU_MS = 1_800L

        /**
         * TRẦN CỨNG: bấm micro mà KHÔNG NÓI GÌ thì bao lâu tự tắt.
         *
         * ⚠ Không có trần này thì đồng hồ một giây ở trên không bao giờ chạy — nó chỉ
         *   khởi động sau khi nghe được một câu. Người bệnh bấm micro rồi bỏ đi là mic
         *   mở suốt, đúng cái "thu tạp âm" mà cả chế độ bấm-mới-nghe sinh ra để tránh.
         */
        private const val TRAN_MO_MIC_MS = 15_000L

        private val tayCam = Handler(Looper.getMainLooper())
        private var henTatMic: Runnable? = null

        private fun huyHenTatMic() {
            henTatMic?.let { tayCam.removeCallbacks(it) }
            henTatMic = null
        }

        /** Bấm micro mà không nói gì — tắt sau TRAN_MO_MIC_MS. */
        private fun henTatMicNeuKhongAiNoi() {
            huyHenTatMic()
            val r = Runnable {
                henTatMic = null
                if (!dangChoNghe) return@Runnable
                Log.d(TAG, "Mở mic $TRAN_MO_MIC_MS ms mà không nghe được gì — tự tắt")
                tatMicro()
                /* Hết giờ mà từ lúc mở mic CHƯA nghe được chữ nào → nói thẳng ra trên màn
                   hình (11/09/2026). Có nghe được mẩu nào mà chưa thành câu thì vẫn chỉ
                   tắt như cũ — trường hợp đó không phải "robot điếc". */
                if (lucNgheCuoi >= lucMoMic) Cau.baoMicTuTat() else Cau.baoMicKhongNgheDuoc()
            }
            henTatMic = r
            tayCam.postDelayed(r, TRAN_MO_MIC_MS)
        }

        /** Người bệnh vừa dứt một vế — hẹn một giây nữa tắt mic, trừ khi có vế tiếp theo. */
        private fun henTatMicSauKhiDutCau(khiTat: () -> Unit) {
            huyHenTatMic()
            val r = Runnable {
                henTatMic = null
                if (!dangChoNghe) return@Runnable
                Log.d(TAG, "Người bệnh dứt câu — tự tắt mic sau $CHO_DUT_CAU_MS ms")
                tatMicro()
                Cau.baoMicTuTat()      // để nút micro trên màn hình thôi nhấp nháy
                khiTat()
            }
            henTatMic = r
            tayCam.postDelayed(r, CHO_DUT_CAU_MS)
        }

        /**
         * ⚠ Mic và "nghe không cần đánh thức" phải đi CÙNG NHAU, đặt ở đúng một chỗ này.
         *
         * `isEnableWakeFree` mặc định BẬT. Bật nghĩa là robot nghe và tự đáp mà không cần
         * từ đánh thức — ở app Mông Dương chính nó gây ra chuyện **lúc dẫn đường robot tự
         * nói bằng giọng AI**, chồng lên giọng đọc SkillApi của app: dọc đường có ai nói
         * câu gì là AgentOS bắt lời rồi trả lời luôn.
         *
         * Ở bệnh viện chuyện đó còn tệ hơn: sảnh đông, robot bắt lời người lạ giữa lúc
         * đang dẫn một người bệnh đi.
         *
         * Tắt hẳn thì màn Trò chuyện mất khả năng nghe liên tục. Nên buộc nó bám theo
         * trạng thái mic: mic mở mới cho nghe tự do, mic đóng là câm hẳn.
         */
        private fun datMicro(tat: Boolean) {
            runCatching {
                /*
                 * ⚠⚠ KHÔNG ĐỤNG VÀO `isMicrophoneMuted` — BẪY MỘT CHIỀU.
                 *   Đặt `= true` là tắt luôn công tắc micro của MÁY và `= false` không bật
                 *   lại được. Chi tiết ở AppAgent.onCreate. Bản trước của app này có đặt,
                 *   nên máy nào đã chạy bản ≤ v1.4 thì đang bị bịt mic: bấm tay nút
                 *   "Bật microphone" trên dải trạng thái của hãng một lần, hoặc chạy
                 *   `adb shell settings put global microphone 1`.
                 *
                 * Chặn nghe nay bằng hai thứ đảo chiều được: isEnableWakeFree, và cờ
                 * dangChoNghe chốt ở onASRResult.
                 */
                AgentCore.isEnableWakeFree = !tat

                /* ═══ VÌ SAO KHÔNG DÙNG `enableWakeupMode` ═══
                   Bật nó là chuyển sang chế độ CHỈ NGHE SAU KHI ĐƯỢC GỌI TÊN, mà tên đánh
                   thức nằm ở Settings.Global `robot_settings_wake_keyword` — trên máy này
                   là {"wake_word":"小豹小豹"}, tiếng Trung. Không người bệnh nào gọi được
                   câu đó, và SDK không có API kích hoạt thẳng. Đường duy nhất là nghe tự
                   do. Gọi tắt ở đây cho chắc, phòng khi ROM bật sẵn.

                   ⚠ ĐIỀU KIỆN VẬN HÀNH của nghe tự do: log ghi `isVisionEnable: true`,
                     tức robot dùng CAMERA để quyết định câu nói có hướng vào nó không.
                     NGƯỜI NÓI PHẢI ĐỨNG ĐỐI DIỆN ROBOT, TRONG TẦM CAMERA. Đứng cạnh sườn
                     hay nói vọng từ xa thì bị xếp 环境音 (tiếng ồn môi trường) và câu nói
                     bị bỏ. Đây là hành vi của hãng, không sửa bằng mã — phải dặn người
                     trực sảnh. */
                runCatching { AgentCore.enableWakeupMode(false) }

                Log.d(TAG, "Micro: chờ nghe=$dangChoNghe · nghe tự do=${AgentCore.isEnableWakeFree}")
            }.onFailure { Log.w(TAG, "Không đặt được trạng thái micro: ${it.message}") }
        }

        fun micDangMo(): Boolean = dangChoNghe

        /** Xoá ngữ cảnh hội thoại — gọi mỗi khi robot về màn chờ.
         *  Ở bệnh viện đây không chỉ là chuyện gọn gàng: chuyện bệnh tình của người trước
         *  không được dính sang lượt của người sau. */
        fun xoaNguCanh() = runCatching { AgentCore.clearContext() }.isSuccess

        /**
         * Kể cho mô hình biết trên màn hình đang có gì.
         *
         * ⚠ Kênh này CHỈ là mô tả màn hình đang hiện, KHÔNG phải kho tri thức. Hãng xác
         *   nhận (phản hồi 11/08/2026, câu A10) mô hình không dùng nó để trả lời câu hỏi
         *   kiến thức. Sự thật cố định (ba toà nhà, phạm vi robot đi được) phải đặt trong
         *   PERSONA; nội dung khoa phòng đi theo danh sách ứng viên trong prompt, xem TraLoi.
         */
        fun moTaManHinh(mo: String) {
            runCatching { AgentCore.uploadInterfaceInfo(mo) }
                .onSuccess { Log.d(TAG, "Đã gửi mô tả màn hình (${mo.length} ký tự)") }
                .onFailure { Log.w(TAG, "Không gửi được mô tả màn hình: ${it.message}") }
        }

        /**
         * Gọi thẳng mô hình ngôn ngữ, KHÔNG qua bộ hoạch định Action của AgentOS.
         * Đây là đường chính để robot trả lời, và là thứ chính hãng khuyên dùng.
         *
         * ⚠ Khác API 0.2.2 ở ba chỗ, sửa nhầm là không biên dịch được:
         *   · callback đổi từ `TaskCallback` sang `LLMCallback`
         *   · `llm()` thêm tham số `stream: Boolean` trước callback
         *   · callback nay trả về CẢ CÂU TRẢ LỜI (`LLMResponse`), không còn chỉ 0/1
         *
         * ⚠ `LLMConfig.fileSearch` (tham số thứ 4) đã bị BỎ — máy chủ ngừng đọc từ
         *   26/08/2025. Đặt true hay false đều vô nghĩa.
         *
         * ⚠⚠ `businessInfo` (tham số thứ 5) PHẢI là `null`, KHÔNG được là `""`.
         *   Đây là nguyên nhân của hai ngày đứng hình ở app Mông Dương: truyền chuỗi rỗng
         *   thì máy chủ hiểu là "có businessInfo" rồi đi tra một hồ sơ rỗng, tra không ra
         *   nên trả `status=2, result=null` sau ~355 ms — không lỗi, không lý do, không log.
         *   Quy ước mã trả về: **status=1 là thành công**, status=2 là máy chủ thất bại.
         *
         * @param khiXong gọi trên luồng phụ với (câu trả lời, lỗi). Một trong hai là null.
         */
        fun hoiMoHinh(danhSachTin: List<LLMMessage>, hanMs: Long = 20_000L,
                     khiXong: (String?, String?) -> Unit) {
            val ok = runCatching {
                /* 400 token đủ cho hai câu dẫn cộng dòng THU_TUC_ID. 20 giây: quá mức đó thì
                   người bệnh đã bỏ đi rồi, chờ thêm vô nghĩa. */
                val cauHinh = LLMConfig(0.6f, 400, 20, false, null)
                AgentCore.llm(danhSachTin, cauHinh, hanMs, false, object : LLMCallback {
                    override fun onTaskEnd(status: Int, result: LLMResponse?) {
                        val chu = result?.message?.content.orEmpty()
                        val tk = result?.tokenCost
                        Log.d(TAG, "hoiMoHinh: status=$status · ${result?.elapsedTime ?: -1f}s · " +
                                   "token prompt=${tk?.promptTokens ?: -1} " +
                                   "completion=${tk?.completionTokens ?: -1} " +
                                   "total=${tk?.totalTokens ?: -1}")
                        // In nguyên đối tượng trả về: lúc hãng từ chối, lý do nằm ở đây chứ
                        // không ở logcat của AgentService. Đây là thứ để gửi cho hãng.
                        Log.d(TAG, "hoiMoHinh: nguyên văn = $result")
                        if (status == 1 && chu.isNotBlank()) {
                            lucAIThongCuoi = System.currentTimeMillis(); loiAICuoi = ""
                            if (!agentSanSang) { agentSanSang = true; Cau.baoAISanSang(true) }
                            khiXong(chu, null)
                        } else {
                            val loi = listOfNotNull(
                                "status=$status",
                                result?.status?.takeIf { it.isNotBlank() }?.let { "trạng thái=$it" },
                                result?.error?.takeIf { it.isNotBlank() }?.let { "lỗi=$it" },
                                if (result == null) "không có dữ liệu trả về" else null
                            ).joinToString(" · ")
                            loiAICuoi = loi       // giữ nguyên văn cho bảng tự chẩn đoán
                            khiXong(null, loi)
                        }
                    }
                })
            }.isSuccess
            if (!ok) { loiAICuoi = "không gọi được mô hình"; khiXong(null, loiAICuoi) }
        }

        /**
         * CHẨN ĐOÁN — hỏi mô hình một câu vu vơ để biết đường lên đám mây có thông không.
         * Gõ trong DevTools:  CAU.thuLLM('Một cộng một bằng mấy?', false)
         * Xem kết quả:        adb logcat -s BVApp
         */
        fun thuLLM(cauHoi: String, @Suppress("UNUSED_PARAMETER") khongDungNua: Boolean) {
            Log.d(TAG, "thuLLM: hỏi '$cauHoi'")
            hoiMoHinh(
                listOf(
                    LLMMessage(Role.SYSTEM, "Bạn là robot lễ tân. Trả lời ngắn gọn bằng tiếng Việt."),
                    LLMMessage(Role.USER, cauHoi)
                )
            ) { chu, loi ->
                Log.d(TAG, "thuLLM: ${if (chu != null) "TRẢ LỜI ĐƯỢC: $chu" else "HỎNG: $loi"}")
                Cau.guiLoiNoi("robot",
                    if (chu != null) "[chẩn đoán] $chu" else "[chẩn đoán] Mô hình không trả lời: $loi",
                    true)
            }
        }

        /**
         * Thăm dò một lần lúc khởi động: mô hình đám mây có trả lời thật không.
         * Chỉ khi trả về status=1 thì lớp web mới hiện nút micro.
         *
         * Đây là phép thử ĐẦU-CUỐI, không phải kiểm tra "hàm có tồn tại không".
         * Bài học từ app Mông Dương: `webkitSpeechRecognition` CÓ tồn tại trong WebView
         * robot, `batMic()` gọi được, `AppAgent` dựng được — nhưng bấm nút mic thì chết
         * câm suốt hai tuần. **Mọi phép kiểm "có hàm không" đều đánh lừa.**
         */
        private fun thamDoAI() {
            tayCam.postDelayed({ thamDoMotLan() }, 4_000L)   // chờ Agent SDK bind xong rồi mới thử
        }

        /**
         * ⚠ THỬ LẠI CHO TỚI KHI THÔNG (sửa 11/09/2026).
         *
         * Bản trước thăm dò ĐÚNG MỘT LẦN, bốn giây sau khi mở app. Lúc đó mạng mà còn chậm —
         * robot vừa khởi động, Wi-Fi chưa nối lại, hay đang chạy 4G — thì lần thử đó hỏng,
         * cờ agentSanSang nằm im ở false, và NÚT MICRO BIẾN MẤT cho tới khi tình cờ có ai gõ
         * một câu hỏi làm mô hình trả lời được. Nhìn từ ngoài đúng như anh Trường tả: "vào
         * app thì mất nút mic, đợi một lúc sau mới hiện".
         *
         * Nay hỏng thì hẹn 20 giây thử lại; thông rồi thì thôi, không gọi đám mây nữa.
         */
        /**
         * ⚠⚠ PHẢI CÓ ĐỒNG HỒ RIÊNG, KHÔNG CHỈ HẸN LẠI TRONG CALLBACK (sửa 18/09/2026).
         *
         * Đo máy thật hôm nay: mở app, chờ hơn một phút, nút micro không hiện, và logcat
         * KHÔNG có lấy một dòng "Thăm dò AI lần …" — kể cả dòng báo hỏng. AgentCore.llm()
         * nhận lệnh lúc bốn giây sau khi mở app, lúc SDK chưa bind xong, rồi NUỐT LUÔN:
         * không callback, không ném lỗi, không log.
         *
         * Bản trước (sửa 11/09) chỉ hẹn thử lại BÊN TRONG callback. Callback không về thì
         * không bao giờ có lần hai. Nhìn từ ngoài giống hệt lỗi cũ, nên dễ tưởng bản sửa
         * trước không ăn thua — thật ra nó mới sửa đúng một nửa vấn đề.
         *
         * Nay mỗi lần thăm dò đặt kèm một đồng hồ: quá hạn mà callback chưa về thì coi như
         * hỏng và thăm dò tiếp. Callback về muộn sau đó cũng không sao — cờ daTraLoi giữ
         * cho mỗi lượt chỉ được xử đúng một lần.
         */
        private fun thamDoMotLan() {
            if (agentSanSang) return
            lanThamDo++
            val lan = lanThamDo
            val daTraLoi = java.util.concurrent.atomic.AtomicBoolean(false)

            /* Lưới an toàn: callback im hẳn thì đồng hồ này gọi lượt sau. */
            tayCam.postDelayed({
                if (!agentSanSang && daTraLoi.compareAndSet(false, true)) {
                    Log.w(TAG, "Thăm dò AI lần $lan: KHÔNG CÓ HỒI ÂM sau $HAN_THAM_DO_MS ms " +
                               "— nhiều khả năng Agent SDK chưa bind xong. Thử lại.")
                    loiAICuoi = "mô hình không hồi âm"
                    thamDoMotLan()
                }
            }, HAN_THAM_DO_MS)

            hoiMoHinh(listOf(LLMMessage(Role.USER, "Xin chào"))) { chu, loi ->
                if (!daTraLoi.compareAndSet(false, true)) return@hoiMoHinh   // đồng hồ đã xử
                Log.d(TAG, "Thăm dò AI lần $lan: " +
                           (if (chu != null) "THÔNG — $chu" else "chưa thông — $loi"))
                Cau.baoAISanSang(chu != null)
                if (chu == null) tayCam.postDelayed({ thamDoMotLan() }, THAM_DO_LAI_MS)
            }
        }

        /** Nghỉ giữa hai lần thăm dò khi mô hình có trả lời nhưng trả lời là "chưa được". */
        private const val THAM_DO_LAI_MS = 20_000L

        /**
         * Hạn chờ hồi âm của MỘT lần thăm dò.
         *
         * Đặt 25 giây vì hạn của chính lời gọi llm() là 20 giây (xem hoiMoHinh) — ngắn hơn
         * mức đó thì đồng hồ này cướp lời một lần gọi vẫn đang chạy bình thường.
         */
        private const val HAN_THAM_DO_MS = 25_000L

        /** "wifi" · "4g" · "khac" · "mat" (không có mạng) · "?" (không đọc được). */
        private fun loaiMang(): String {
            return runCatching {
                val cm = boiCanh?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                        as? android.net.ConnectivityManager ?: return "?"
                val mang = cm.activeNetwork ?: return "mat"
                val kn = cm.getNetworkCapabilities(mang) ?: return "?"
                when {
                    kn.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    kn.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "4g"
                    else -> "khac"
                }
            }.getOrDefault("?")
        }

        /**
         * Công tắc micro CỦA MÁY — Settings.Global "microphone": 1 bật · 0 TẮT · -1 không đọc được.
         * Đây là thứ bị bẫy isMicrophoneMuted kéo về 0, và cũng là thứ nút micro trên dải trạng
         * thái của hãng bật/tắt. Về 0 thì app làm gì cũng điếc.
         */
        private fun micHeThong(): Int = runCatching {
            android.provider.Settings.Global.getInt(boiCanh!!.contentResolver, "microphone", -1)
        }.getOrDefault(-1)

        /** Thông tin để tự kiểm lúc lắp đặt, và để lớp web quyết định có hiện nút mic không. */
        fun thongTinAI(): String {
            val appId = runCatching { AgentCore.appId }.getOrNull()
            val bayGio = System.currentTimeMillis()
            fun giayTruoc(luc: Long): Long = if (luc == 0L) -1L else (bayGio - luc) / 1000
            return """{"appId":${org.json.JSONObject.quote(appId ?: "")},""" +
                   """"coAgent":${agent != null},""" +
                   """"agentSanSang":$agentSanSang,""" +
                   """"micDangMo":${micDangMo()},""" +
                   """"mang":"${loaiMang()}",""" +
                   """"micHeThong":${micHeThong()},""" +
                   """"loiAI":${org.json.JSONObject.quote(loiAICuoi)},""" +
                   """"lanThamDo":$lanThamDo,""" +
                   """"ngheCuoiGiay":${giayTruoc(lucNgheCuoi)},""" +
                   """"aiThongCuoiGiay":${giayTruoc(lucAIThongCuoi)}}"""
        }

        /* ══════════════════════════════════════════════════════════════
           BA CHỐT AN TOÀN — chặn bằng MÃ, không chặn bằng lời dặn

           Vì sao không tin vào setObjective: ở app Mông Dương đã dặn rõ *"kho chưa có
           hộ tịch, gặp mấy việc đó phải nói rõ là chưa được nạp, tuyệt đối đừng đoán"*,
           nhưng đo ngày 11/08/2026: hỏi "tôi muốn làm giấy khai sinh cho con" thì robot
           KHÔNG gọi Action, tự trả lời luôn *"Anh cần chuẩn bị: tờ khai đăng ký khai
           sinh, giấy chứng…"* — bịa từ kiến thức nền của mô hình.

           Ở bệnh viện, cái giá của một câu bịa cao hơn nhiều so với ở uỷ ban.
           ══════════════════════════════════════════════════════════════ */

        /**
         * ① KHẨN CẤP — chặn trước mọi thứ khác.
         *
         * Trung tâm hành chính công không phải bệnh viện, nhưng sảnh chờ đông người già
         * ngồi đợi hàng giờ, và chuyện có người ngất là chuyện đã xảy ra ở nhiều nơi.
         * Robot không sơ cứu được gì — việc duy nhất nó làm đúng là HÔ TO cho cán bộ
         * nghe thấy và nhắc số 115, rồi im.
         *
         * Cố ý CHỈ bắt những từ không thể hiểu nhầm sang một thủ tục hành chính. Không
         * đưa "tai nạn" trơn vào đây: "hỗ trợ nạn nhân tai nạn giao thông" là một thủ
         * tục trợ cấp có thật, chặn nhầm là người dân không tra được việc của mình.
         */
        private val TU_KHAN_CAP = Regex(
            "cấp cứu|cap cuu|nguy kịch|nguy kich|ngất|ngat xiu|bất tỉnh|bat tinh|" +
            "co giật|co giat|đột quỵ|dot quy|tai biến|tai bien|" +
            "không thở được|khong tho duoc|khó thở quá|kho tho qua|ngạt thở|ngat tho|" +
            "chảy máu nhiều|chay mau nhieu|băng huyết|bang huyet|" +
            "ngộ độc|ngo doc|gọi cứu thương|goi cuu thuong|gọi 115|goi 115|" +
            "có người ngã|co nguoi nga|cháy|chay nha|hoả hoạn|hoa hoan",
            RegexOption.IGNORE_CASE
        )

        /**
         * ② XIN ROBOT QUYẾT THAY CÁN BỘ — không bao giờ được trả lời.
         *
         * Đây là lớp thay cho lớp "hỏi bệnh" của app bệnh viện, và lý do tồn tại giống
         * hệt: có loại câu mà một câu trả lời sai gây hậu quả thật cho người hỏi.
         *
         *   · "tôi có được hưởng trợ cấp không"  → quyết định hưởng là của UBND xã sau
         *      khi thẩm định hồ sơ. Robot đoán "được" thì người dân bỏ buổi làm đi nộp.
         *   · "hồ sơ của tôi đến đâu rồi"        → robot không nối vào phần mềm một cửa,
         *      không có cách nào biết.
         *   · "khai hộ tôi", "làm giúp tôi"      → việc của cán bộ tiếp nhận.
         *   · "trường hợp của tôi thì sao"       → tư vấn pháp lý cho một ca cụ thể.
         *
         * ⚠ HẸP CÓ CHỦ Ý. Phải có chữ chỉ NGƯỜI HỎI (tôi/em/cháu/nhà tôi) hoặc lời nhờ
         *   làm hộ. "Hộ nghèo được hưởng gì" là câu tra cứu hợp lệ và phải trả lời được.
         */
        private val TU_XIN_QUYET_DINH = Regex(
            "(tôi|em|cháu|nhà tôi|bố tôi|mẹ tôi|con tôi) có (được|đủ điều kiện|thuộc diện)|" +
            "(toi|em|chau) co (duoc|du dieu kien|thuoc dien)|" +
            "tôi có được hưởng|toi co duoc huong|có được duyệt không|co duoc duyet khong|" +
            "hồ sơ của (tôi|em|cháu)|ho so cua (toi|em|chau)|" +
            "hồ sơ (tôi|em) (đến đâu|tới đâu|xong chưa|duyệt chưa)|" +
            "(đến đâu rồi|tới đâu rồi|xong chưa|duyệt chưa|giải quyết chưa)|" +
            "(khai|điền|viết|làm|nộp) (hộ|giúp|giùm|thay) (tôi|em|cháu)|" +
            "(khai|dien|viet|lam|nop) (ho|giup|gium) (toi|em|chau)|" +
            "trường hợp của (tôi|em|cháu|nhà tôi)|truong hop cua (toi|em|chau)|" +
            "(tôi|em) nên (làm|chọn|khai) (gì|thế nào|sao)",
            RegexOption.IGNORE_CASE
        )

        /**
         * ③ CHƯA CÓ DỮ LIỆU — thứ chỉ cán bộ trả lời được.
         *
         * ⚠ Nhắm vào câu hỏi SỐ LIỆU VẬN HÀNH, không nhắm vào từ khoá thủ tục. "Quầy ba
         *   ở đâu" là câu dẫn đường hợp lệ; "trung tâm mấy giờ đóng cửa" mới là câu chưa
         *   có dữ liệu — vì giờ làm việc chưa ai khai vào app, và đoán sai thì người dân
         *   đi mấy chục cây số tới nơi gặp cửa đóng.
         *
         * ⚠ LỆ PHÍ nằm ở đây là CHỦ Ý, nhưng lớp này đứng SAU trợ lý ngoài và kho nạp
         *   trước (xem TraLoi.xuLy). Hai kho đó có lệ phí kèm căn cứ pháp lý; chỉ khi cả
         *   hai không trả lời được thì mới tới đây nói "chưa có".
         */
        private val TU_CHUA_CO = Regex(
            "mấy giờ|may gio|giờ làm việc|gio lam viec|giờ mở cửa|mở cửa lúc|đóng cửa lúc|" +
            "làm việc thứ mấy|lam viec thu may|thứ bảy có làm|chủ nhật có làm|nghỉ trưa|" +
            "cán bộ nào|can bo nao|anh nào|chị nào|ai trực|ai tiếp|lịch trực|lich truc|" +
            "tên (cán bộ|anh|chị)|số điện thoại|so dien thoai|hotline|gọi cho ai|" +
            "đặt lịch|dat lich|đặt hẹn|dat hen|lấy số trước|lay so truoc|" +
            "còn bao nhiêu người|con bao nhieu nguoi|đợi bao lâu|doi bao lau|" +
            "đông không|dong khong|vắng không|hôm nay có đông",
            RegexOption.IGNORE_CASE
        )

        /**
         * CÂU KHÔNG ĐƯỢC TRA MẠNG — thủ tục · pháp luật · việc nội bộ Trung tâm.
         *
         * Anh Trường chốt 22/09/2026: tra Google chỉ cho câu đời thường. Câu dính tới thủ
         * tục hay luật thì chỉ được trả lời bằng dữ liệu chuyên biệt đã nạp (kho trong
         * robot + nguồn thutuc.hanhchinhso.ai.vn) — không bao giờ bằng một trang web lạ.
         *
         * ⚠ CỐ Ý RỘNG TAY: nghi là thủ tục thì coi là thủ tục. Chặn nhầm một câu đời thường
         *   chỉ làm robot nói "chưa có"; để lọt một câu thủ tục ra web là robot có thể đọc
         *   sai giấy tờ cho người dân.
         * ⚠ TRÁNH CHUỖI CON HAI NGHĨA — đã rà từng từ:
         *     "phí"  nằm trong "phía"            → chỉ dùng "lệ phí", "phí làm"
         *     "kiện" nằm trong "sự kiện", "điều kiện" → chỉ dùng "khởi kiện", "đi kiện"
         *     "quay" (không dấu) là "quay lại"  → chỉ dùng "quầy"
         *     "trung tâm" trần là "trung tâm thương mại" → chỉ dùng "trung tâm này/phục vụ/hành chính"
         * ⚠ Có bản sao ở gia-lap-robot.js — tools/doi-chieu-lop-chan.py so từng ký tự.
         */
        private val TU_KHONG_TRA_MANG = Regex(
            "thủ tục|thu tuc|hồ sơ|ho so|giấy tờ|giay to|giấy phép|giay phep|tờ khai|to khai|" +
            "mẫu đơn|mau don|đăng ký|dang ky|chứng thực|chung thuc|công chứng|cong chung|sao y|" +
            "khai sinh|khai tử|khai tu|kết hôn|ket hon|ly hôn|ly hon|hôn nhân|hon nhan|độc thân|" +
            "hộ tịch|ho tich|tạm trú|tam tru|tạm vắng|tam vang|thường trú|thuong tru|cư trú|cu tru|" +
            "hộ khẩu|ho khau|căn cước|can cuoc|cccd|cmnd|hộ chiếu|ho chieu|" +
            "sổ đỏ|sổ hồng|đất đai|dat dai|thửa đất|tách thửa|tach thua|sang tên|sang ten|" +
            "thừa kế|thua ke|di chúc|di chuc|giám hộ|giam ho|con nuôi|con nuoi|nhận cha|" +
            "kinh doanh|thuế|lệ phí|le phi|phí làm|trợ cấp|tro cap|hộ nghèo|ho ngheo|cận nghèo|" +
            "người có công|nguoi co cong|mai táng|bảo trợ|bảo hiểm|bao hiem|" +
            "luật|nghị định|nghi dinh|thông tư|thong tu|quy định|quy dinh|pháp lý|phap ly|" +
            "xử phạt|xu phat|bị phạt|vi phạm|vi pham|tòa án|toa an|khởi kiện|khoi kien|đi kiện|" +
            "khiếu nại|khieu nai|tố cáo|to cao|công an|cong an|ubnd|ủy ban|uỷ ban|uy ban|" +
            "một cửa|mot cua|quầy|cán bộ|can bo|xác nhận|xac nhan|nộp|" +
            "trung tâm này|trung tâm phục vụ|trung tâm hành chính|ở đây|" +
            "bạn là ai|ban la ai|bạn tên|tên bạn|robot",
            RegexOption.IGNORE_CASE
        )

        /**
         * CÂU CHÍNH TRỊ — robot từ chối, không tra mạng, không hỏi nguồn.
         * Chỉ xét SAU TU_KHONG_TRA_MANG: "kết hôn với người nước ngoài" là thủ tục, phải đi
         * đường thủ tục, không được rơi vào đây vì chữ "nước ngoài".
         * ⚠ KHÔNG có "chế độ": "chế độ người có công" là thủ tục thật.
         */
        private val TU_CHINH_TRI = Regex(
            "chính trị|chinh tri|đảng cộng sản|dang cong san|đảng viên|tổng bí thư|tong bi thu|" +
            "chủ tịch nước|chu tich nuoc|thủ tướng|thu tuong|bộ chính trị|quốc hội|quoc hoi|" +
            "bầu cử|bau cu|biểu tình|bieu tinh|phản động|phan dong|nhân quyền|nhan quyen|" +
            "đa đảng|da dang|biển đông|bien dong|hoàng sa|trường sa|chủ quyền|chu quyen|" +
            "tuyên truyền|lãnh đạo nhà nước|chính phủ|chinh phu|tổng thống|tong thong",
            RegexOption.IGNORE_CASE
        )

        private const val LOI_CHINH_TRI =
            "Câu này thuộc chủ đề chính trị, tôi là robot hướng dẫn thủ tục nên xin phép " +
            "không bàn ạ. Anh chị cần làm giấy tờ gì, tôi chỉ giúp."

        private const val LOI_TRA_MANG_KHONG_DUOC =
            "Câu này nằm ngoài phần tôi được nạp, và tôi chưa tra được thông tin đáng tin " +
            "trên mạng. Anh chị hỏi thêm cán bộ ở quầy hướng dẫn giúp tôi ạ."

        /**
         * Chế độ AgentOS: bốn lớp chặn chạy trên CHỮ NGƯỜI DÂN NÓI, trước khi AgentOS kịp
         * đáp. Trúng thì app nói câu của app và CẮT AgentOS cả lượt.
         */
        /* ── TỪ ĐÁNH THỨC ── */
        @Volatile private var lucDanhThucCuoi = 0L
        /** Hai lần đánh thức cách nhau ít nhất chừng này — một câu chào bị nghe thành hai vế. */
        private const val DANH_THUC_NGHI_MS = 8_000L

        /**
         * Câu NGẮN có "xin chào" (có dấu hoặc không). Câu dài quá 6 từ thì không tính: người
         * trong sảnh nói "…xin chào chị, hôm nay chị đi đâu…" với nhau không được đánh thức robot.
         */
        fun laLoiDanhThuc(chu: String): Boolean {
            val t = chu.lowercase().replace(Regex("[^\\p{L}\\p{N} ]+"), " ").trim().replace(Regex("\\s+"), " ")
            if (t.isEmpty() || t.split(' ').size > 6) return false
            return t.contains("xin chào") || t.contains("xin chao")
        }

        fun chanTruocAgentOS(cau: String): Boolean {
            // ⚠ KHÔNG xoá cờ ở đây — AgentOS có thể đã gọi Action trước lúc app gửi câu.
            //   Cờ xoá ở đầu lượt: batMicro() và Cau.hoiAgentOS().
            val chan = chanKhanCapNeuCan(cau) || chanXinQuyetDinhNeuCan(cau) ||
                       chanChinhTriNeuCan(cau) ||
                       (!Cai.CHE_DO_PORTAL && chanChuaCoDuLieuNeuCan(cau))
            if (chan) { appDaDapLuotNay = true; runCatching { AgentCore.stopTTS() } }
            return chan
        }

        /** Trả true nếu đã chặn xong câu chính trị. */
        fun chanChinhTriNeuCan(cau: String): Boolean {
            if (TU_KHONG_TRA_MANG.containsMatchIn(cau)) return false
            if (!TU_CHINH_TRI.containsMatchIn(cau)) return false
            Log.w(TAG, "CHẶN CHÍNH TRỊ: '$cau'")
            tuDoc(LOI_CHINH_TRI)
            return true
        }

        /** Câu này có được đem đi tra mạng không. */
        fun nenTraMang(cau: String): Boolean =
            !TU_KHONG_TRA_MANG.containsMatchIn(cau) &&
            !TU_CHINH_TRI.containsMatchIn(cau) &&
            !TU_CHUA_CO.containsMatchIn(cau) &&
            !TU_KHAN_CAP.containsMatchIn(cau)

        /** Câu trả lời từ mạng mà trôi sang chính trị thì vứt. */
        fun dapMangCoChinhTri(chu: String): Boolean = TU_CHINH_TRI.containsMatchIn(chu)

        fun loiTraMangKhongDuoc() = LOI_TRA_MANG_KHONG_DUOC

        /* ⚠ Ba câu dưới đây ghi cứng trong mã là CỐ Ý: lớp chặn phải trả lời được kể cả
           khi WebView chưa nạp xong hay dữ liệu hỏng. Đổi lại, chúng KHÔNG tự theo
           app-data.json — đổi cách tổ chức quầy thì phải sửa cả ở đây. */

        private const val LOI_KHAN_CAP =
            "Có người cần giúp gấp. Mời cán bộ ra hỗ trợ ngay giúp tôi. " +
            "Nếu cần cấp cứu, anh chị gọi số một một năm. " +
            "Tôi là robot, tôi không sơ cứu được, anh chị đừng chờ tôi."

        private const val LOI_XIN_QUYET_DINH =
            "Việc này tôi không dám trả lời thay cán bộ đâu ạ. " +
            "Hồ sơ đủ hay chưa, có được duyệt không, thì cán bộ tiếp nhận xem giấy tờ thật " +
            "mới kết luận được. Anh chị mang giấy tờ tới quầy, tôi chỉ giúp cần chuẩn bị những gì."

        private const val LOI_CHUA_CO =
            "Phần này tôi chưa được nạp dữ liệu nên không dám nói, sợ sai thì anh chị mất công. " +
            "Mời anh chị hỏi quầy hướng dẫn giúp tôi ạ."

        /**
         * Câu mà CHÍNH APP vừa cho robot đọc.
         *
         * Robot đọc câu nào thì onTTSResult cũng bắn từng mẩu chữ về ("Phần này tôi",
         * "Phần này tôi chưa được nạp"…). Câu do app tự soạn thì app đã đưa lên màn hình
         * rồi; để mẩu chữ bắn về nữa là khung chat hiện lặp bốn năm lần cùng một câu.
         */
        @Volatile private var tuNoi: String = ""

        /**
         * ⚠ NHỚ BA CÂU GẦN NHẤT app đã đọc, không chỉ câu cuối.
         * Đo ở app Medinova 27/08/2026: luồng chữ TTS của câu CŨ còn chảy về ~1 giây sau
         * khi câu mới đã bắt đầu. Chỉ nhớ câu cuối thì mẩu chữ cũ bị xếp nhầm là "AgentOS
         * tự nói" và ăn stopTTS() — robot tự cắt tiếng chính mình.
         *
         * Ghi nhớ đặt ở RobotHelper.doc() — cửa DUY NHẤT ra loa — chứ không ở tuDoc(),
         * vì Cau.kt còn 4 chỗ gọi thẳng RobotHelper.doc() (đọc thẻ, lời dẫn đường).
         */
        /** Chế độ AgentOS: lượt này app đã tự đáp (qua Action hoặc lớp chặn) → cắt AgentOS. */
        @Volatile var appDaDapLuotNay = false

        /**
         * CỬA SỔ LƯỢT của chế độ AgentOS. Đo 22/09/2026: bật hoạch định là AgentOS tự nghe
         * tiếng nói chuyện trong phòng và tự đáp dù micro của app đang đóng (17 lần / 3 phút).
         * Chỉ trong cửa sổ này AgentOS mới được nói và gọi Action.
         */
        @Volatile var lucMoLuotAgentOS = 0L
        private const val CUA_SO_LUOT_MS = 30_000L
        fun moLuotAgentOS() {
            lucMoLuotAgentOS = System.currentTimeMillis(); appDaDapLuotNay = false
            agentOSDaNoiLuotNay = false; soLuotAgentOS++
        }

        /** AgentOS đã nói ít nhất một mẩu chữ trong lượt này — đường lui khỏi chen vào. */
        @Volatile var agentOSDaNoiLuotNay = false
        /** Đếm lượt: đường lui của lượt cũ tới hạn khi đã sang lượt mới thì bỏ. */
        @Volatile private var soLuotAgentOS = 0

        /**
         * Hẹn ĐƯỜNG LUI: quá Cai.CHO_AGENTOS_MS mà AgentOS chưa nói gì → app tự trả lời bằng
         * dữ liệu nạp sẵn. Đặt appDaDapLuotNay = true trước, để câu AgentOS về muộn bị cắt
         * (onTTSResult) — không thì robot trả lời hai lần.
         */
        fun henDuongLuiAgentOS(cau: String) {
            val luot = soLuotAgentOS
            tayCam.postDelayed({
                if (luot != soLuotAgentOS || appDaDapLuotNay || agentOSDaNoiLuotNay || !nhanCauTraLoi) return@postDelayed
                Log.w(TAG, "AgentOS IM ${Cai.CHO_AGENTOS_MS} ms — app tự trả lời bằng dữ liệu nạp sẵn: '$cau'")
                appDaDapLuotNay = true
                runCatching { AgentCore.stopTTS() }
                TraLoi.hoi(cau)
            }, Cai.CHO_AGENTOS_MS)
        }
        fun trongLuotAgentOS() = System.currentTimeMillis() - lucMoLuotAgentOS < CUA_SO_LUOT_MS

        private const val SO_CAU_NHO = 3
        private val tuNoiGanDay = java.util.ArrayDeque<String>()

        fun ghiNhoTuNoi(cau: String) {
            tuNoi = cau
            synchronized(tuNoiGanDay) {
                tuNoiGanDay.addFirst(cau)
                while (tuNoiGanDay.size > SO_CAU_NHO) tuNoiGanDay.removeLast()
            }
        }

        /**
         * Mẩu chữ TTS này có phải câu app vừa đọc không.
         *
         * ⚠⚠ ĐỪNG so chuỗi con. Đo trên robot 42DABA16 ngày 22/09/2026: app đọc
         *   "Cần làm rõ⏎Bạn muốn hỏi…" (có XUỐNG DÒNG), chữ TTS bắn về là "Cần làm rõ Bạn"
         *   (DẤU CÁCH) → contains() trượt → cái chặn tưởng là AgentOS và CẮT TIẾNG CỦA CHÍNH
         *   APP, 6 lần trong 2 phút. TTS còn có thể đổi "60" thành "sáu mươi".
         *   So theo TỈ LỆ TỪ TRÙNG: ≥ 60% từ của mẩu chữ nằm trong câu app → là của app.
         *   AgentOS tự soạn câu trả lời bằng lời của nó nên hiếm khi trùng tới mức đó.
         */
        private fun tuCua(s: String): List<String> =
            s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
             .split(' ').filter { it.isNotEmpty() }

        private fun laChuCuaApp(chu: String): Boolean {
            val w = tuCua(chu)
            /* Mẩu CHỈ có dấu câu ("?", "-", "}}") — đo trên robot 22/09/2026, đó là đuôi luồng
               chữ TTS của câu app vừa đọc. Không có từ nào thì không thể là AgentOS đang nói;
               coi là của app, KHÔNG cắt. Trả false ở đây từng làm robot tự cắt tiếng mình. */
            if (w.isEmpty()) return true
            val can = kotlin.math.ceil(w.size * 0.6).toInt()
            synchronized(tuNoiGanDay) {
                return tuNoiGanDay.any { cau ->
                    val tap = tuCua(cau).toHashSet()
                    w.count { it in tap } >= can
                }
            }
        }

        private fun quenTuNoi() {
            tuNoi = ""
            synchronized(tuNoiGanDay) { tuNoiGanDay.clear() }
        }

        /**
         * ROBOT ĐỌC MỘT CÂU DO CHÍNH APP SOẠN.
         * Gom về một chỗ vì ba việc phải đi cùng nhau, thiếu một là lỗi:
         *   · nhớ câu vừa đọc (tuNoi) để onTTSResult khỏi đẩy lên màn hình lần nữa
         *   · đọc thành tiếng bằng SkillApi — đường này chạy tốt từ đầu, không đụng vào
         *   · hiện luôn lên khung chat cho người đứng xa đọc được
         */
        /**
         * Action cho AgentOS gọi ngược vào app — đường DUY NHẤT để AgentOS dùng dữ liệu dự
         * án thay vì kho Portal. ActionResult có trường `result` (Bundle, soi bằng javap) để
         * mang nội dung về. ⚠ Tên tham số không được trùng thuộc tính của Action/Parameter
         * (name, desc, type, required, parameters, executor, displayName, appId).
         */
        private fun actionTraCuu() = Action(
            name = "vn.roboworld.hcc.TRA_CUU_THU_TUC",
            displayName = "Tra cứu thủ tục",
            desc = "Tra cứu thủ tục hành chính cấp xã trong dữ liệu của Trung tâm: giấy tờ cần " +
                   "chuẩn bị, trình tự, nơi nộp. Gọi mỗi khi người dân hỏi về thủ tục hành chính.",
            parameters = listOf(Parameter("cau_hoi", ParameterType.STRING,
                                          "Nguyên văn câu hỏi của người dân về thủ tục", true)),
            executor = object : ActionExecutor {
                override fun onExecute(action: Action, params: Bundle?): Boolean {
                    val q = params?.getString("cau_hoi").orEmpty()
                    val t0 = System.currentTimeMillis()
                    if (!trongLuotAgentOS()) {
                        Log.w(TAG, "AgentOS gọi Action NGOÀI LƯỢT (nghe lỏm trong phòng) — bỏ: '$q'")
                        action.notify(ActionResult(status = ActionStatus.FAILED, result = Bundle()), false)
                        return true
                    }
                    Log.w(TAG, "AGENTOS GỌI ACTION tra cứu: '$q'")
                    appDaDapLuotNay = true          // từ đây AgentOS tự nói gì cũng bị cắt
                    runCatching { AgentCore.stopTTS() }
                    Thread {
                        val kq = runCatching { TraLoi.traDuLieu(q) }.getOrNull()
                        val b = Bundle()
                        if (kq != null) {
                            b.putString("noi_dung", kq.second)
                            b.putString("chi_tiet", kq.first.take(1500))
                            Cau.hienBanDayDu(kq.first, kq.third)
                            // menu hỏi lại → nút bấm, y như đường thường
                            runCatching {
                                val ds = org.json.JSONObject(Cau.locCauNgoai(kq.first)).optJSONArray("lua_chon")
                                if (ds != null && ds.length() > 0) Cau.hienLuaChonNgoai(ds.toString())
                            }
                            /* APP TỰ ĐỌC — đo 22/09: AgentOS bỏ qua dữ liệu trong ActionResult. */
                            tuDoc(kq.second)
                        } else {
                            b.putString("noi_dung", "Không có dữ liệu cho câu hỏi này. Mời người dân hỏi quầy hướng dẫn.")
                            tuDoc(TraLoi.cauKhongCo())
                        }
                        Log.w(TAG, "ACTION trả về ${if (kq != null) "CÓ" else "KHÔNG CÓ"} dữ liệu sau " +
                                   "${System.currentTimeMillis() - t0} ms: ${b.getString("noi_dung")?.take(80)}")
                        action.notify(ActionResult(status = ActionStatus.SUCCEEDED, result = b), false)
                    }.start()
                    return true
                }
            }
        )

        fun tuDoc(cau: String) {
            if (cau.isBlank()) return
            /* CỬA RA của mọi lời robot nói. Người bệnh đã thao tác (quay lại, thoát,
               xoá đoạn chat, bấm mic nói tiếp) thì lượt hỏi cũ coi như bỏ — câu trả lời
               về muộn phải câm, đừng đọc đè lên việc họ đang làm. */
            if (!choPhepNoi()) {
                Log.d(TAG, "Lượt hỏi đã bị bỏ — không đọc câu: ${cau.take(40)}…")
                return
            }
            RobotHelper.doc(cau) {}      // RobotHelper.doc tự ghiNhoTuNoi
            Cau.guiLoiNoi("robot", cau, true)
        }

        /** Trả true nếu đã chặn xong — đừng hỏi mô hình nữa. */
        fun chanKhanCapNeuCan(cau: String): Boolean {
            if (!TU_KHAN_CAP.containsMatchIn(cau)) return false
            Log.w(TAG, "CHẶN KHẨN CẤP: '$cau'")
            runCatching { AgentCore.stopTTS() }
            Cau.moManCapCuu()          // bật màn đỏ để người đứng xa cũng thấy
            tuDoc(LOI_KHAN_CAP)
            return true
        }

        fun chanXinQuyetDinhNeuCan(cau: String): Boolean {
            if (!TU_XIN_QUYET_DINH.containsMatchIn(cau)) return false
            Log.w(TAG, "CHẶN XIN QUYẾT ĐỊNH THAY CÁN BỘ: '$cau'")
            runCatching { AgentCore.stopTTS() }
            tuDoc(LOI_XIN_QUYET_DINH)
            return true
        }

        fun chanChuaCoDuLieuNeuCan(cau: String): Boolean {
            if (!TU_CHUA_CO.containsMatchIn(cau)) return false
            Log.w(TAG, "CHẶN CHƯA CÓ DỮ LIỆU: '$cau'")
            runCatching { AgentCore.stopTTS() }
            tuDoc(LOI_CHUA_CO)
            return true
        }

        /* ══════════════════════════════════════════════════════════════
           LỜI DẶN GỬI MÔ HÌNH — dùng chung cho setPersona và cho TraLoi
           ══════════════════════════════════════════════════════════════ */

        /**
         * ⚠ NHỮNG SỰ THẬT CỐ ĐỊNH PHẢI ĐẶT Ở ĐÂY, KHÔNG ĐẶT Ở uploadInterfaceInfo.
         * Hãng xác nhận kênh uploadInterfaceInfo chỉ là MÔ TẢ MÀN HÌNH ĐANG HIỆN, mô hình
         * không dùng nó để trả lời câu hỏi kiến thức.
         *
         * Quy tắc: sự thật KHÔNG ĐỔI (ba toà nhà, phạm vi robot đi được) → đây.
         *          Thứ THAY ĐỔI theo màn hình → uploadInterfaceInfo.
         *          Vị trí khoa phòng → danh sách ứng viên trong prompt, xem TraLoi.kt.
         */
        /** Persona cho bản thử Portal — KHÔNG trói vào "danh sách ứng viên" như PERSONA. */
        val PERSONA_PORTAL =
            "Bạn là robot hướng dẫn của Trung tâm Phục vụ Hành chính công xã Tây Hòa, tỉnh Đắk Lắk. " +
            "Bạn đứng ở sảnh, giúp người dân biết thủ tục cần giấy tờ gì và nộp ở đâu. " +
            "Bạn không phải cán bộ: không tiếp nhận hồ sơ, không kết luận hồ sơ đủ hay thiếu, " +
            "không hứa ngày có kết quả. Xưng tôi, gọi người dân là anh chị."

        val PERSONA =
            "Bạn là robot hướng dẫn của Trung tâm Phục vụ Hành chính công xã Tây Hòa, " +
            "tỉnh Đắk Lắk. Bạn đứng ở sảnh, ngay chỗ người dân vừa bước vào.\n" +

            "Việc của bạn có ĐÚNG BA thứ: nói cho người dân biết một thủ tục cần giấy tờ gì, " +
            "chỉ họ tới đúng quầy, và dẫn họ tới quầy đó nếu họ nhờ. Ngoài ba việc ấy thì " +
            "bạn mời họ hỏi cán bộ.\n" +

            "Trung tâm có bảy quầy tiếp nhận, đánh số từ quầy một tới quầy bảy. " +
            "Mỗi thủ tục nộp ở một quầy; số quầy do máy đọc nguyên văn từ bảng phân công, " +
            "bạn không tự nói số quầy.\n" +

            "BẠN KHÔNG PHẢI CÁN BỘ. Bạn không tiếp nhận hồ sơ, không thẩm định giấy tờ, " +
            "không kết luận hồ sơ đủ hay thiếu, không hứa bao giờ có kết quả, không biết " +
            "hồ sơ của ai đang ở đâu. Mấy việc đó chỉ cán bộ ngồi quầy làm được.\n" +

            "BẠN KHÔNG TƯ VẤN PHÁP LUẬT cho một trường hợp cụ thể, không đoán ai đủ điều " +
            "kiện hưởng chế độ gì. Bạn chỉ nói thủ tục nói chung cần những gì.\n" +

            "Người tới đây phần nhiều là người lớn tuổi, nhiều người mang theo cả xấp giấy " +
            "tờ và đã đi một quãng xa. Bạn lễ độ, kiên nhẫn, nói chậm, mỗi lần một ý. " +
            "Bạn xưng 'tôi', gọi người đối diện là 'anh chị'. Mỗi câu trả lời không quá hai câu."

        /**
         * Luật gửi kèm mỗi lần gọi mô hình.
         *
         * ⚠ Luật số 4 — dòng THU_TUC_ID — là thứ để app KIỂM BẰNG MÃ, không phải cho đẹp.
         *   Mô hình trả id không nằm trong danh sách ứng viên, hoặc quên hẳn dòng này, thì
         *   app vứt cả câu trả lời và đọc câu từ chối của mình. Xem TraLoi.docCauTraLoi().
         *   Sai thì sai theo hướng im lặng.
         *
         * ⚠ Luật số 2 — cấm nói số tầng và mã phòng. Đây là phần KHÔNG được sai: chỉ lệch
         *   một tầng là người bệnh leo nhầm. App tự đọc phần đó nguyên văn từ bảng khoa
         *   phòng bệnh viện cung cấp, và trong prompt cũng không gửi câu đọc đó đi.
         */
        val LUAT_TRA_LOI =
            "LUẬT BẮT BUỘC:\n" +
            "1. Chỉ được nói về thủ tục có trong DANH SÁCH ỨNG VIÊN gửi kèm bên dưới. " +
            "Tuyệt đối không nhắc tên thủ tục nào khác, không nhớ từ chỗ khác, không suy ra.\n" +
            "2. KHÔNG tự nói số quầy, số ngày giải quyết, lệ phí, danh sách giấy tờ. Ngay sau " +
            "câu của bạn, máy sẽ tự đọc phần đó lấy nguyên văn từ bảng thủ tục niêm yết. " +
            "Bạn nói nữa là anh chị nghe hai lần, mà lệch một con số là họ mang thiếu giấy tờ " +
            "và phải đi lại lần nữa.\n" +
            "3. Câu của bạn chỉ là CÂU DẪN ngắn, tối đa hai câu, dễ nghe với người lớn tuổi. " +
            "Ví dụ: \"Dạ tôi hiểu rồi ạ.\" hoặc \"Việc này anh chị làm ngay tại trung tâm được ạ.\"\n" +
            "4. DÒNG CUỐI CÙNG của câu trả lời BẮT BUỘC viết đúng dạng:\n" +
            "   THU_TUC_ID: X\n" +
            "   X là id của thủ tục bạn chọn trong danh sách ứng viên;\n" +
            "   hoặc KHONG_CO nếu anh chị hỏi chuyện thủ tục mà không ứng viên nào khớp;\n" +
            "   hoặc TAM_SU nếu anh chị chỉ nói chuyện ngoài lề.\n" +
            "Không hứa thay cán bộ, không đoán kết quả hồ sơ, không bàn chuyện chính trị."

    }

    override fun onCreate() {
        super.onCreate()
        boiCanh = applicationContext

        // Tầng thấp: dẫn đường + đọc thành tiếng. Đây mới là phần app sống bằng.
        RobotHelper.ketNoi(this)
        // Pin · du hành · tự về lễ tân · về trạm sạc (29/09/2026)
        DiChuyen.batDau(this)

        // Tầng cao: nghe và gọi mô hình
        agent = object : AppAgent(this) {

            override fun onCreate() {

                /*
                 * ═══ BỐN CÔNG TẮC CỦA HÃNG, CẢ BỐN ĐỀU MẶC ĐỊNH SAI VỚI APP NÀY ═══
                 *
                 * Đặt ở đây là an toàn: `create$sdk_release` gán `api` rồi mới gọi `onCreate`,
                 * nên giá trị đặt tại đây nằm luôn trong gói `AppInfo` gửi sang AgentService.
                 *
                 * 1. isEnableVoiceBar — MẶC ĐỊNH BẬT. Đây là hộp trắng góc phải dưới màn hình
                 *    robot: phụ đề câu đang đọc kèm nút "Chạm để Ngắt". Nó ĐÈ LÊN giao diện
                 *    app. App đã có khung chat và video biểu cảm riêng nên thanh này chỉ vướng.
                 *
                 * 2. isDisablePlan — bật để TẮT bộ hoạch định của AgentOS. Bắt buộc phải tắt
                 *    khi app tự gọi `AgentCore.llm()`, không thì hai bên cùng trả lời một câu
                 *    hỏi và robot nói chồng lên chính nó.
                 *    ⚠ Đổi lại: từ đây AgentOS KHÔNG còn gọi Action nào nữa. Đường chính là
                 *      onASRResult → TraLoi.hoi(). App này vì thế KHÔNG đăng ký Action riêng.
                 *    ⚠ Chốt kiểm sau khi cài: nói vào robot vẫn phải thấy `Người bệnh nói: …`
                 *      trong logcat. Mất dòng đó nghĩa là tắt hoạch định giết luôn ASR —
                 *      đã đo ở app Mông Dương là KHÔNG giết, nhưng vẫn phải kiểm lại trên máy này.
                 *
                 * 3. isEnableWakeFree — mặc định BẬT, robot tự bắt lời người đi ngang.
                 *    Tắt từ đầu; chỉ bật khi người bệnh bấm nút micro. Xem datMicro().
                 *
                 * ⚠⚠ CÔNG TẮC THỨ TƯ — `isMicrophoneMuted` — ĐÃ GỠ BỎ 08/09/2026.
                 *
                 *    Bản trước đặt `isMicrophoneMuted = true` ngay tại đây, với ý "đóng mic
                 *    từ đầu, chỉ mở ở màn Trò chuyện". Nghe thì hợp lý, nhưng đó là BẪY MỘT
                 *    CHIỀU đã đo trên máy thật 26/08/2026 (app sự kiện Long An, rồi app
                 *    Medinova bản 1.0 dính lại):
                 *
                 *      · đặt `= true`  → TẮT LUÔN công tắc micro của MÁY
                 *                        (Settings.Global "microphone" 1 → 0, icon mic trên
                 *                         dải trạng thái của hãng hiện gạch chéo)
                 *      · đặt `= false` → KHÔNG bật lại được. Khoá vẫn nằm ở 0.
                 *
                 *    Nghĩa là chỉ cần chạy app một lần là robot tự bịt tai mình VĨNH VIỄN,
                 *    cho tới khi có người bấm tay nút "Bật microphone" trên màn robot, hoặc
                 *    chạy `adb shell settings put global microphone 1`. Không tầng nào báo
                 *    lỗi: app vẫn ghi "tắt tiếng=false", AgentService vẫn nhận chữ, chỉ có
                 *    `onASRResult` là không bao giờ chạy.
                 *
                 *    Việc "robot đừng bắt lời ngoài màn Trò chuyện" nay làm bằng hai thứ
                 *    KHÁC, cả hai đều đảo chiều được:
                 *      · isEnableWakeFree — có bắt lời tự do không
                 *      · cờ dangChoNghe   — chốt ở onASRResult
                 */
                isEnableVoiceBar = false
                isDisablePlan = !Cai.CHE_DO_AGENTOS     // chế độ AgentOS: để nó hoạch định
                isEnableWakeFree = false
                Log.d(TAG, "Công tắc hãng: voiceBar=$isEnableVoiceBar · disablePlan=$isDisablePlan · " +
                           "wakeFree=$isEnableWakeFree  (KHÔNG đụng isMicrophoneMuted)")

                /* Persona vẫn đặt dù đã tắt hoạch định: nó là đường lui nếu phải bật
                   disablePlan=false lại, và không tốn gì. */
                setPersona(PERSONA)
                setObjective(
                    "Bạn là robot hướng dẫn tại Trung tâm Phục vụ Hành chính công xã Tây Hòa. " +
                    "KHÔNG tự trả lời bất cứ câu nào — mọi câu trả lời do ứng dụng soạn. " +
                    "Không dùng kho kiến thức chung của nền tảng.\n" + LUAT_TRA_LOI
                )

                // Hai Action nói chuyện có sẵn của hệ thống. App KHÔNG đăng ký Action riêng:
                // đã tắt hoạch định thì AgentOS không gọi Action nào nữa, đăng ký cũng vô ích.
                registerAction(Actions.SAY)
                /* KNOWLEDGE_QA = AgentOS được đọc kho kiến thức trên Robot Portal — cấu hình
                   THEO DOANH NGHIỆP (hãng, mục A9), tức kho DÙNG CHUNG của mọi robot trong
                   tài khoản. Chế độ AgentOS phải bỏ nó. ⚠ Đo 26/08 ở app khác: bỏ cả SAY lẫn
                   KNOWLEDGE_QA thì mất đường nghe — bản thử này giữ SAY, bỏ KNOWLEDGE_QA,
                   và phải đo lại xem robot còn nghe không. */
                if (!Cai.CHE_DO_AGENTOS || Cai.GIU_KNOWLEDGE_QA) registerAction(Actions.KNOWLEDGE_QA)
                if (Cai.CHE_DO_AGENTOS && Cai.CHE_DO_PORTAL) {
                    setPersona(PERSONA_PORTAL)
                    setObjective(
                        "Bạn là robot hướng dẫn tại Trung tâm Phục vụ Hành chính công xã Tây Hòa, Đắk Lắk. " +
                        "Trả lời câu hỏi của người dân dựa trên KHO KIẾN THỨC đã được nạp. " +
                        "Trả lời ngắn gọn, lịch sự, dễ nghe với người lớn tuổi, tối đa ba câu. " +
                        "Kho không có thông tin thì nói thẳng là chưa có và mời hỏi quầy hướng dẫn, " +
                        "không tự đoán giấy tờ, lệ phí hay thời hạn. Không bàn chính trị."
                    )
                    Log.w(TAG, "CHẾ ĐỘ PORTAL: hoạch định BẬT · KNOWLEDGE_QA=${Cai.GIU_KNOWLEDGE_QA} · KHÔNG có Action dữ liệu dự án")
                } else if (Cai.CHE_DO_AGENTOS) {
                    registerAction(actionTraCuu())
                    setObjective(
                        "Bạn là robot hướng dẫn tại Trung tâm Phục vụ Hành chính công xã Tây Hòa, Đắk Lắk. " +
                        "Mỗi khi người dân hỏi về thủ tục hành chính, giấy tờ, hồ sơ, lệ phí, nơi nộp, " +
                        "BẮT BUỘC gọi hành động vn.roboworld.hcc.TRA_CUU_THU_TUC với nguyên văn câu hỏi, " +
                        "rồi trả lời NGẮN GỌN, lịch sự, CHỈ dựa trên kết quả hành động trả về. " +
                        "Kết quả báo không có dữ liệu thì nói thẳng là chưa có và mời hỏi quầy hướng dẫn. " +
                        "TUYỆT ĐỐI không tự trả lời thủ tục bằng kiến thức riêng. Không bàn chính trị."
                    )
                    Log.w(TAG, "CHẾ ĐỘ AgentOS: hoạch định BẬT · KNOWLEDGE_QA=${Cai.GIU_KNOWLEDGE_QA} · Action tra cứu đã đăng ký")
                }

                /*
                 * ĐÂY LÀ CÁI MIC.
                 *
                 * Đừng quay lại webkitSpeechRecognition của trình duyệt: trên WebView robot
                 * hàm đó TỒN TẠI nhưng gọi start() là ném ngay error=not-allowed (Android
                 * WebView không có dịch vụ nhận dạng giọng nói, chỉ Chrome thật mới có).
                 *
                 * ⚠ Bản 0.2.2 chỉ có MỘT hàm `onTranscribe`, phân biệt hai chiều bằng cờ
                 *   `isUserSpeaking`. Bản 0.4.7 tách hẳn làm hai hàm — trình biên dịch bắt
                 *   được ngay nếu viết nhầm.
                 *
                 * Trả false để hệ thống XỬ LÝ TIẾP như bình thường — trả true là nuốt mất
                 * câu nói.
                 *
                 * ⚠ Callback này chạy trên LUỒNG PHỤ. Đụng tới WebView phải post về luồng
                 *   chính — Cau.guiLoiNoi đã lo việc đó.
                 */
                setOnTranscribeListener(object : OnTranscribeListener {

                    override fun onASRResult(t: Transcription): Boolean {
                        val chu = t.text
                        if (chu.isNullOrBlank()) return false
                        lucNgheCuoi = System.currentTimeMillis()   // cho bảng tự chẩn đoán

                        // Nghe được chữ nghĩa là đường dây thật sự thông.
                        if (!agentSanSang) { agentSanSang = true; Cau.baoAISanSang(true) }

                        /* CHỐT BẤM-MỚI-NGHE. Mic đóng thì nghe được cũng bỏ.
                           ⚠ Đây là chốt MIC, không phải chốt MÀN HÌNH. Ngay trong màn Trò
                             chuyện mic cũng đóng cho tới khi người bệnh bấm nút, nên câu
                             lọt vào lúc đó rơi vào đây. Ghi rõ cả hai để người đọc log sau
                             này không tưởng app đang ở nhầm màn.
                           Đây cũng là thứ THAY CHO việc tắt micro bằng isMicrophoneMuted —
                           xem chú thích dài ở datMicro(). */
                        if (!dangChoNghe) {
                            /* TỪ ĐÁNH THỨC "XIN CHÀO" — anh Trường yêu cầu 24/09/2026.
                               Không dùng enableWakeupMode của hãng: nó bắt gọi tên robot bằng
                               TIẾNG TRUNG (小豹小豹) — xem bẫy trong CLAUDE.md. Thay vào đó nghe
                               ngay ở đây: mic app đóng nhưng hãng VẪN chuyển chữ về (đo 22/09:
                               47 câu / 60 giây), và chỉ chuyển khi camera thấy người đối diện. */
                            if (t.final && Cau.manDang == "mh-cho" && laLoiDanhThuc(chu)) {
                                val bay = System.currentTimeMillis()
                                if (bay - lucDanhThucCuoi > DANH_THUC_NGHI_MS) {
                                    lucDanhThucCuoi = bay
                                    Log.w(TAG, "TỪ ĐÁNH THỨC: '$chu' — chào lại, mở màn chính")
                                    DiChuyen.coTuongTac()      // đang du hành / về lễ tân thì dừng lại
                                    Cau.danhThucBangLoiChao()
                                    return false
                                }
                            }
                            Log.d(TAG, "Nghe được '$chu' nhưng mic đang đóng — bỏ qua")
                            return false
                        }

                        Cau.guiLoiNoi("nguoi", chu, t.final)

                        if (t.final) {
                            Log.d(TAG, "Người bệnh nói: $chu")
                            quenTuNoi()         // lượt mới, quên các câu app tự đọc lần trước

                            /* BẤM-MỚI-NGHE: gom vế, hẹn một giây nữa tắt mic rồi MỚI gửi
                               cả câu đi. Mỗi `final` mới ĐẶT LẠI cái hẹn, nên người bệnh
                               nói bao nhiêu vế cũng gom đủ.

                               ĐƯỜNG CHÍNH của cả cuộc hội thoại nằm ở guiCauDaGom → TraLoi:
                               cấp cứu → hỏi bệnh → chưa có dữ liệu → tra kho → chấm tin cậy
                               → hỏi mô hình → kiểm câu trả lời → mới cho robot đọc.
                               TraLoi tự đẩy sang luồng phụ nên callback này không bị chặn. */
                            gomVe(chu)
                            henTatMicSauKhiDutCau { guiCauDaGom() }

                            /* ⚠ ĐỪNG trả `true` ở đây để "nuốt" câu nói. Đã thử 26/08/2026
                               ở app sự kiện Long An, đúng nhằm chặn AgentOS trả lời hai
                               lần: HỎNG — app mất luôn đường nghe, onASRResult không chạy
                               lần nào nữa, robot điếc hẳn. Việc chặn AgentOS tự đáp làm ở
                               ĐẦU RA (onTTSResult bên dưới), không làm ở đầu vào. */
                        }
                        return false
                    }

                    override fun onTTSResult(t: Transcription): Boolean {
                        val chu = t.text
                        if (chu.isNullOrBlank()) return false
                        /* Câu do chính app soạn thì đã hiện trên màn hình rồi — xem tuNoi.
                           ⚠ ĐỪNG xoá tuNoi ở đây khi t.final: robot đọc xong TỪNG CÂU là bắn
                           một gói final, nên xoá ngay câu đầu thì mấy câu sau lọt lưới và màn
                           hình lại hiện lặp. tuNoi chỉ xoá khi người bệnh mở miệng lần sau. */
                        if (laChuCuaApp(chu)) return false
                        if (Cai.CHE_DO_AGENTOS && !trongLuotAgentOS()) {
                            Log.w(TAG, "AgentOS tự nói NGOÀI LƯỢT — cắt: '$chu'")
                            runCatching { AgentCore.stopTTS() }
                            return false
                        }
                        if (Cai.CHE_DO_AGENTOS && appDaDapLuotNay) {
                            Log.w(TAG, "AgentOS nói chen lượt app đã đáp — cắt: '$chu'")
                            runCatching { AgentCore.stopTTS() }
                            return false
                        }
                        if (Cai.CHE_DO_AGENTOS) {
                            agentOSDaNoiLuotNay = true
                            DiChuyen.coHoatDong()
                            if (t.final) Log.w(TAG, "AgentOS nói: $chu")
                            Cau.guiLoiNoi("robot", chu, t.final)
                            return false
                        }

                        /* ═══ CHẶN AgentOS TỰ TRẢ LỜI — lọc ở ĐẦU RA (sửa 22/09/2026) ═══
                         *
                         * Tới đây nghĩa là robot đang đọc một câu KHÔNG phải của app. Chỉ có
                         * một nguồn như vậy: AgentOS tự đáp. isDisablePlan không cấm được
                         * (đo 335 lượt ở app Long An), và nó có thể trả lời bằng KHO KIẾN THỨC
                         * DÙNG CHUNG trên Robot Portal — nơi đang có tài liệu HUTECH và hỏi–đáp
                         * của app khác. Robot Tây Hòa chỉ được nói dữ liệu của chính nó.
                         *
                         * Bản trước ở đây ĐẨY câu đó LÊN MÀN HÌNH như lời của app. Nay cắt tiếng
                         * ngay từ mẩu chữ đầu, và không hiện gì.
                         *
                         * ⚠ Không chặn ở onASRResult / không bỏ registerAction(KNOWLEDGE_QA):
                         *   cả hai cách đều giết luôn đường nghe (đo 26/08/2026). */
                        Log.w(TAG, "AgentOS tự trả lời '$chu' — cắt tiếng, không hiện")
                        runCatching { AgentCore.stopTTS() }
                        return false
                    }
                })
            }

            /** App này không mở Action nào cho app khác gọi vào. */
            override fun onExecuteAction(action: Action, params: Bundle?): Boolean = false
        }

        thamDoAI()
    }
}
