plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.roboworld.hcc"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.roboworld.hcc"
        minSdk = 26          // Android 8.0 — mức tối thiểu Agent SDK yêu cầu
        targetSdk = 34
        versionCode = 8
        versionName = "1.6.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    // index.html đã nén sẵn 1,5 MB; để nguyên cho WebView đọc thẳng từ assets
    androidResources { noCompress += listOf("html") }
}

dependencies {
    /*
     * Agent SDK — tầng hội thoại AI của OrionStar. Lấy từ GÓI OFFLINE của hãng,
     * nằm ở libs/ (sdk-0.4.7.aar + agent-base-0.2.10.aar).
     *
     * ⚠ TUYỆT ĐỐI KHÔNG quay lại JitPack `com.github.orionagent:agent-sdk`.
     *   Cả bốn bản trên đó (0.2.2 · 0.2.3 · 0.2.4 · 0.2.5) đều GHI CỨNG sai tên gói
     *   dịch vụ — `com.ainirobot.speechasrservice` trong khi ROM máy này để dịch vụ ở
     *   `com.ainirobot.agentservice`. SDK không bind được, nên mọi lệnh `AgentCore.*`
     *   lặng lẽ không làm gì: không lỗi, không callback, không log. App tra cứu thủ tục
     *   câm micro suốt hai tuần vì đúng chuyện này.
     *   Hãng xác nhận 11/08/2026: JitPack là kênh cũ thời 0.2.x, đã ngừng bảo trì.
     *   Cần bản mới hơn 0.4.7 thì xin hãng gói offline mới, đừng tự tìm trên mạng.
     *
     * Robot SDK — tầng điều khiển phần cứng (dẫn đường, giọng nói): robotservice.jar.
     * ⚠ robotservice.jar cần Gson nhưng KHÔNG đóng gói kèm — thiếu dòng gson bên dưới
     *   thì RobotApi.connectServer ném ClassNotFoundException, app văng ngay lúc kết nối.
     */
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
