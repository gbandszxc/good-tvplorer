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
    }
}

rootProject.name = "GoodTVplorer"
include(":app")

// Load project-local overrides (gitignored local.properties)
val localProps = file("local.properties")
if (localProps.exists()) {
    val props = java.util.Properties().apply { load(localProps.inputStream()) }
    // sdk.dir is read by AGP; java.home (and others) we inject as system properties
    props.filter { it.key.toString() != "sdk.dir" }.forEach { (key, value) ->
        System.setProperty(key.toString(), value.toString())
    }
}
