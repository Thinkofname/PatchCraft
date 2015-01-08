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
import uk.co.thinkofdeath.patchtools.disassemble.Disassembler
import uk.co.thinkofdeath.patchtools.PatchScope
import kotlin.util.measureTimeMillis
import java.security.MessageDigest
import java.math.BigInteger
import java.net.URL

val MC_LOCATION = "https://s3.amazonaws.com/Minecraft.Download/versions/%1\$s/%1\$s.%2\$s";

fun createPatchedJar(): File? {
    val minecraftFolder = getMinecraftLocation()
    val targetVersion = File(minecraftFolder, "versions/$MINECRAFT_VERSION/")
    if (!targetVersion.exists()) {
        targetVersion.mkdirs()
        val jar = File(targetVersion, "$MINECRAFT_VERSION.jar")
        val jarURL = URL(MC_LOCATION.format(MINECRAFT_VERSION, "jar"))
        val ver = File(targetVersion, "$MINECRAFT_VERSION.json")
        val verURL = URL(MC_LOCATION.format(MINECRAFT_VERSION, "jar"))

        jar.writeBytes(jarURL.readBytes())
        ver.writeBytes(verURL.readBytes())
    }

    println("Finding patches")

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

    println("Checking for cached build")
    val hash = MessageDigest.getInstance("SHA1")

    patches.forEach {
        hash.update(it.value)
    }

    val hashHex = BigInteger(hash.digest()).toString(16)

    val jarStore = File(self.getParentFile(), "jars")
    if (!jarStore.exists()) jarStore.mkdirs()

    val outJar = File(jarStore, "$hashHex.jar")
    if (outJar.exists()) {
        println("Found $hashHex, using...")
        return outJar
    }
    println("Could not find $hashHex, building...")


    println("Loading original jar")
    val jar = File(targetVersion, "$MINECRAFT_VERSION.jar")
    val classes = ClassSet(ClassPathWrapper(jar))
    val resources = hashMapOf<String, ByteArray>()

    val dis = Disassembler(classes)

    ZipFile(jar).use {
        val entries = it.entries()
        while (entries.hasMoreElements()) {
            var file = entries.nextElement()
            if ((!file.getName().contains("/") ||
                file.getName().startsWith("net/minecraft")) &&
                file.getName().endsWith(".class")) {
                classes.add(it.getInputStream(file))

                if (java.lang.Boolean.getBoolean("patchcraft.dis")) {
                    val name = file.getName().substring(0, file.getName().length() - 6)
                    val f = File(self.getParentFile(), "dis/$name.jpatch")
                    if (!f.getParentFile().exists()) f.getParentFile().mkdirs()
                    f.writeText(
                        dis.disassemble(name)
                    )
                }
            } else {
                it.getInputStream(file).use {
                    resources.put(file.getName(), it.readBytes())
                }
            }
        }
    }

    println("Applying patches")
    val patcher = Patcher(classes)
    val scope = PatchScope()

    patches.forEach {
        val time = measureTimeMillis {
            print("Applying ${it.key}")
            scope.merge(patcher.apply(it.value.inputStream))
        }
        println(" [$time ms]")
    }

    println("Saving")

    ZipOutputStream(FileOutputStream(outJar)).use {
        val zip = it
        classes.classes(true).forEach {
            if (java.lang.Boolean.getBoolean("patchcraft.map")) {
                val mapped = classes.getClass(it, scope)
                var name = scope.getClass(classes.getClassWrapper(it)!!)
                if (name == null) {
                    name = it
                }
                zip.putNextEntry(ZipEntry("$name.class"))
                zip.write(mapped)
            } else {
                zip.putNextEntry(ZipEntry("$it.class"))
                zip.write(classes.getClass(it))
            }
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

