plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.json:json:20250517")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
