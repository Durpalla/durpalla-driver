// Build tooling must not carry known advisories either (OSV-checked): raise vulnerable transitive versions
// pulled in by AGP / plugins on the buildscript classpath.
buildscript {
    dependencies {
        constraints {
            listOf(
                "io.netty:netty-codec:4.1.137.Final", "io.netty:netty-codec-http:4.1.137.Final", "io.netty:netty-codec-http2:4.1.137.Final",
                "io.netty:netty-common:4.1.137.Final", "io.netty:netty-handler:4.1.137.Final", "io.netty:netty-handler-proxy:4.1.137.Final",
                "io.netty:netty-buffer:4.1.137.Final", "io.netty:netty-transport:4.1.137.Final", "io.netty:netty-resolver:4.1.137.Final",
                "io.netty:netty-codec-socks:4.1.137.Final", "io.netty:netty-transport-native-unix-common:4.1.137.Final",
                "com.google.protobuf:protobuf-java:3.25.9", "commons-io:commons-io:2.22.0", "org.apache.commons:commons-compress:1.28.0",
                "org.bitbucket.b_c:jose4j:0.9.7", "org.jdom:jdom2:2.0.6.1",
                "org.bouncycastle:bcprov-jdk18on:1.86", "org.bouncycastle:bcpkix-jdk18on:1.86", "org.bouncycastle:bcutil-jdk18on:1.86",
            ).forEach { classpath(it) }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
}
