pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ⚠ KHÔNG thêm jitpack.io vào đây. Agent SDK lấy từ gói offline của hãng, để ở
        //   app/libs/. Mọi bản agent-sdk trên JitPack đều ghi cứng sai tên gói dịch vụ
        //   nên AgentCore im lặng không làm gì — xem chú thích trong app/build.gradle.kts.
    }
}
rootProject.name = "HccTrungTam"
include(":app")
