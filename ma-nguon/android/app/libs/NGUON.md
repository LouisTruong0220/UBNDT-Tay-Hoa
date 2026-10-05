# robotservice.jar

Lấy từ `https://github.com/OrionStarGIT/RobotSample.git` → `app/libs/robotservice.jar`
Ngày lấy: 07/08/2026 · 1,2 MB · 825 lớp · ngày build trong jar: 15/05/2026

## Đã đối chiếu với code

Mở jar ra kiểm tra từng chữ ký hàm mà `RobotHelper.kt` gọi — tất cả khớp:

| Gọi trong code | Có thật trong jar |
|---|---|
| `startNavigation(reqId, tenDiem, 0.2, 30_000L, listener)` | `int startNavigation(int, String, double, long, ActionListener)` ✅ |
| `stopNavigation(reqId)` | `int stopNavigation(int)` ✅ |
| `isRobotEstimate(reqId, listener)` | `int isRobotEstimate(int, CommandListener)` ✅ |
| `getPlaceList(reqId, listener)` | `int getPlaceList(int, CommandListener)` ✅ |
| `connectServer(context, listener)` | `void connectServer(Context, ApiListener)` ✅ |
| `setCallback(callback)` | `void setCallback(ModuleCallbackApi)` ✅ |

**Kiểu khai báo callback** (chỗ Kotlin dễ sai giữa `Interface {}` và `Class() {}`):

| Lớp | Loại | Viết trong Kotlin |
|---|---|---|
| `ApiListener` | **interface** | `object : ApiListener { }` — không ngoặc |
| `ActionListener` | class | `object : ActionListener() { }` — có ngoặc |
| `CommandListener` | class | `object : CommandListener() { }` — có ngoặc |
| `ModuleCallbackApi` | class | `object : ModuleCallbackApi() { }` — có ngoặc |

**9 hằng số `Definition`** dùng trong phần dịch mã lỗi đều có thật: `RESULT_OK`,
`ERROR_NOT_ESTIMATE`, `ERROR_DESTINATION_NOT_EXIST`, `ERROR_IN_DESTINATION`,
`ERROR_DESTINATION_CAN_NOT_ARRAIVE`, `ACTION_RESPONSE_ALREADY_RUN`,
`ACTION_RESPONSE_REQUEST_RES_ERROR`, `STATUS_NAVI_AVOID`, `STATUS_NAVI_AVOID_END`.

## Lưu ý về phiên bản

Tài liệu hãng ghi: *"SDK dùng trong Demo phải tải file jar khớp với hệ thống robot"*.
Bản này lấy từ nhánh chính của kho mẫu. Nếu chạy trên máy thật mà gặp lỗi lạ liên quan
tới RobotApi, kiểm tra ROM của máy `M03SHW2A2302525085B6` rồi tải bản jar khớp ở mục
Releases của kho.
