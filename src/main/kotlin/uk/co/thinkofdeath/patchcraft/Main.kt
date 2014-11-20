package uk.co.thinkofdeath.patchcraft

import java.io.File
import javax.swing.JOptionPane
import java.io.FileOutputStream
import java.net.URLClassLoader
import java.util.Arrays
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder

val MINECRAFT_VERSION = "1.8.1-pre5"

fun main(args: Array<String>) {
    if (args.size == 0) {
        val mcFolder = getMinecraftLocation()
        if (!mcFolder.exists()) {
            JOptionPane.showMessageDialog(null,
                "Unable to find a minecraft installation",
                "Error",
                JOptionPane.ERROR_MESSAGE)
            return
        }

        val installFolder = File(mcFolder, "versions/PatchCraft-$MINECRAFT_VERSION/")

        if (!installFolder.exists() && !installFolder.mkdirs()) {
            JOptionPane.showMessageDialog(null,
                "Unable to create target folder",
                "Error",
                JOptionPane.ERROR_MESSAGE)
            return
        }

        val self = File(javaClass<Dummy>()
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()
        )

        self.copyTo(
            File(installFolder, "PatchCraft-$MINECRAFT_VERSION.jar")
        )

        val verJson = javaClass<Dummy>().getResourceAsStream("/version.json")
        if (verJson == null) {
            throw RuntimeException()
        }
        val out = FileOutputStream(File(installFolder, "PatchCraft-$MINECRAFT_VERSION.json"))
            .writer(Charsets.UTF_8)
        out.use {
            val json = verJson
                .reader(Charsets.UTF_8)
                .readText()
                .replaceAll("\\$\\{version\\}", MINECRAFT_VERSION)
            val el = JsonParser().parse(json).getAsJsonObject()
            val orig = File(mcFolder, "versions/$MINECRAFT_VERSION/$MINECRAFT_VERSION.json")
                .reader().use {
                JsonParser().parse(it).getAsJsonObject()
            }

            el.getAsJsonArray("libraries").addAll(
                orig.getAsJsonArray("libraries")
            )

            out.write(GsonBuilder().setPrettyPrinting().create().toJson(el))
        }

        JOptionPane.showMessageDialog(null,
            "Installation complete, please select PatchCraft from your launcher 's " +
                "version selector",
            "Success",
            JOptionPane.INFORMATION_MESSAGE
        )
    } else {
        val jar = createPatchedJar()
        if (jar != null) {
            launchJar(jar, args)
        }
    }
}

fun getMinecraftLocation(): File {
    val os = System.getProperty("os.name", "unknown").toLowerCase()
    val home = System.getProperty("user.home", ".")
    if ("mac" in os) {
        return File(home, "Library/Application Support/minecraft/")
    } else if ("win" in os) {
        val appData = System.getenv("APPDATA")
        return File(appData ?: home, ".minecraft/")
    } else if ("linux" in os || "unix" in os) {
        return File(home, ".minecraft/")
    }
    return File(home, "minecraft/")
}

class Dummy

fun launchJar(jar: File, args: Array<String>) {
    val toLoad = arrayListOf(jar)
    val cl = URLClassLoader(toLoad.map { it.toURI().toURL() }.copyToArray())
    val main = Class.forName("net.minecraft.client.main.Main", true, cl)
    println("Entering minecraft")
    main.getMethod("main", javaClass<Array<String>>()).invoke(null, args)
}