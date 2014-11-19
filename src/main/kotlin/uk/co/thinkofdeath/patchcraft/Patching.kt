package uk.co.thinkofdeath.patchcraft

import java.io.File
import javax.swing.JOptionPane
import uk.co.thinkofdeath.patchtools.Patcher
import uk.co.thinkofdeath.patchtools.wrappers.ClassSet
import uk.co.thinkofdeath.patchtools.wrappers.ClassPathWrapper
import java.util.ArrayList
import com.google.gson.JsonParser
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.Comparator

fun createPatchedJar() : File? {
    val minecraftFolder = getMinecraftLocation()
    val targetVersion = File(minecraftFolder, "versions/$MINECRAFT_VERSION/")
    if (!targetVersion.exists()) {
        // TODO: Download it ourselves?
        JOptionPane.showMessageDialog(null,
            "Unable to find a minecraft $MINECRAFT_VERSION jar, please run that version first",
            "Error",
            JOptionPane.ERROR_MESSAGE)
        return null;
    }

    println("Loading original jar")
    val jar = File(targetVersion, "$MINECRAFT_VERSION.jar")
    val classes = ClassSet(ClassPathWrapper(jar))
    val resources = hashMapOf<String, ByteArray>()

    ZipFile(jar).use {
        val entries = it.entries()
        while (entries.hasMoreElements()) {
            var file = entries.nextElement()
            if ((!file.getName().contains("/") ||
                file.getName().startsWith("net/minecraft")) &&
                file.getName().endsWith(".class")) {
                classes.add(it.getInputStream(file))
            } else {
                it.getInputStream(file).use {
                    resources.put(file.getName(), it.readBytes())
                }
            }
        }
    }

    println("Applying patches")
    val patcher = Patcher(classes)

    val self = File(javaClass<Dummy>()
        .getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .toURI()
    )
    val patches = sortedMapOf<String, ByteArray>()
    ZipFile(self).use {
        val entries = it.entries()
        while (entries.hasMoreElements()) {
            var file = entries.nextElement()
            if (file.getName().endsWith(".jpatch")) {
                it.getInputStream(file).use {
                    patches.set(file.getName(), it.readBytes())
                }
            }
        }
    }

    patches.forEach {
        println("Applying ${it.key}")
        patcher.apply(it.value.inputStream)
    }

    println("Saving")
    var outJar = File.createTempFile("client", ".jar")
    if (outJar.exists()) outJar.delete()

    ZipOutputStream(FileOutputStream(outJar)).use {
        val zip = it
        classes.classes(true).forEach {
            zip.putNextEntry(ZipEntry("$it.class"))
            zip.write(classes.getClass(it))
        }
        for ((k, v) in resources) {
            if (k.startsWith("META-INF/")) continue
            zip.putNextEntry(ZipEntry(k))
            zip.write(v)
        }
    }

    return outJar
}

fun appendMinecraftDependencies(minecraftFolder: File, version: String, deps: ArrayList<File>) {
    val vFile = File(minecraftFolder, "versions/$version/$version.json")
    vFile.reader().use {
        val el = JsonParser().parse(it).getAsJsonObject()
        val libs = el.get("libraries").getAsJsonArray()
        for (e in libs) {
            val lib = e.getAsJsonObject().get("name").getAsString()
            val (pck, name, ver) = lib.split(":")
            val lFile = File(minecraftFolder, "libraries/${pck.replace('.', '/')}/$name/$ver/$name-$ver.jar")
            if (lFile.exists()) {
                deps.add(lFile)
            }
        }
    }
}

