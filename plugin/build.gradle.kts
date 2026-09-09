plugins {
    java
}

group = "io.github.mamble"
version = "0.1.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.119-stable")

    // テスト側でも Bukkit の型 (YamlConfiguration など) を触るのでクラスパスに要る。
    testImplementation("io.papermc.paper:paper-api:26.2.build.119-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Paper 26.x は Java 25 で動作するため、それに合わせる
    options.release = 25
}

val packDir = rootProject.file("pack")

tasks.test {
    useJUnitPlatform()
    // シンボル表とリソースパックの中身が食い違っていないか見るのに使う。
    systemProperty("mamble.packDir", packDir.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// リソースパックを jar に同梱する。プラグインがこれを自分で配信するので、
// 外部にファイルを置く必要も、SHA-1 を手で書く必要も無い。
val resourcePackZip = tasks.register<Zip>("resourcePackZip") {
    description = "同梱用に pack/ を固める"
    from(packDir)
    archiveFileName = "resourcepack.zip"
    destinationDirectory = layout.buildDirectory.dir("resourcepack")
    // 中身が同じなら同じ zip になるようにする。SHA-1 が動くとクライアントが毎回引き直す。
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    // expand の中身は Gradle から見えないので、version を変えただけでは作り直されない。
    // 宣言しておかないと jar の名前だけ新しく、中の paper-plugin.yml は古い版のままになる。
    inputs.property("version", project.version)
    from(resourcePackZip)
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName = "Mamble"
}
