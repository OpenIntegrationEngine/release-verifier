import groovy.io.FileType

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry

enum ValidationStrategy {
    CLASS_DIGEST,
    JAR_HASH,
    COMPILATION,
    SKIP,
}

final DIGEST_PREFIXES = ['oie/client-lib','oie/extensions']

def ensureFileIsDirectory(File dir) {
    if (!dir.exists() || !dir.isDirectory()) {
        println "Error: “${args[0]}” is not a directory"
        System.exit(1)
    }
}

static def tarPathToRepoPath(String path) {
    return path
        .replaceFirst('oie/',               'engine/')
        .replaceFirst('server-lib/donkey', 'donkey/lib')
        .replaceFirst('extensions',         'server/lib/extensions')
        .replaceFirst('server-lib',         'server/lib')
        .replaceFirst('client-lib',         'client/lib')
        .replaceFirst('cli-lib',            'command/lib')
        .replaceFirst('manager-lib',        'manager/lib')
}

static def calculateFileHash(String path) {
    def md = MessageDigest.getInstance("SHA-256")

    new File(path).withInputStream { is ->
        byte[] buffer = new byte[8192]
        int read
        while((read = is.read(buffer)) != -1) {
            md.update(buffer, 0, read)
        }
    }
    return md.digest()
        .collect { byte b -> String.format('%02x', b & 0xff) }
        .join()
}

def handleJarHashStrategy(String tarPath, String repoPath) {
    def tarJarHash = calculateFileHash(tarPath)
    def repoJarHash = calculateFileHash(repoPath)

    if (tarJarHash == repoJarHash) {
        println("Hash verified for $tarPath - $tarJarHash")
    } else {
        println("""Hash verification failed for $tarPath
Expected: $tarJarHash
Calculated: $repoJarHash
""")
        throw new RuntimeException("Hash verification failed for $tarPath")
    }
}

static def getJarClassDigests(String tarPath) {
    def result = new HashSet<Map.Entry<String, String>>()

    new JarFile(tarPath).withCloseable { jar ->
        jar.entries().findAll { e ->
            !e.isDirectory() && e.name.endsWith('.class')
        }.each { entry ->
            def md = MessageDigest.getInstance('SHA-256')
            jar.getInputStream(entry as ZipEntry).withCloseable { is ->
                byte[] buffer = new byte[8192]
                int read
                while((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read)
                }
            }
            // convert bytes to hex
            def hex = md.digest()
                .collect { byte b -> String.format('%02x', b & 0xff) }
                .join()

            def basename = (entry as JarEntry).name
            result.add(new AbstractMap.SimpleImmutableEntry<String, String>(basename, hex))
        }
    }
    return result
}

def handleCompilationStrategy(String tarPath) {
    def repoPath = tarPath.replace("oie/", "engine/server/setup/")
    def tarJar = new JarFile(tarPath)

    def hasClasses = tarJar.entries().iterator().any {
        !it.name.startsWith("META-INF")
    }

    if (!hasClasses) {
        // I don't know what to do here... skip?
        println("$tarPath has no classes in it")
        return
    }

    def tarDigests = getJarClassDigests(tarPath)
    def repoDigests = getJarClassDigests(repoPath)

    if (tarDigests == repoDigests) {
        println("Class digests are equal for $tarPath")
    } else {
        throw new RuntimeException("Class digest verification failed for $tarPath")
    }
}

def clonedRepoDir = new File("engine")
def tarballDir = new File("oie")
def tempDir = new File("temp")

ensureFileIsDirectory(clonedRepoDir)
ensureFileIsDirectory(tarballDir)
ensureFileIsDirectory(tempDir)

def startInstant = Instant.now()

def jarInTarToStrategyMap = new HashMap<String, ValidationStrategy>()

// Get all Jar files from the cloned git repository
tarballDir.traverse(
        type: FileType.FILES,
        nameFilter: ~/.*\.jar/
) { file ->
    def strategy

    // Ignore Install4j files
    if (file.path.contains("install4j")) {
        strategy = ValidationStrategy.SKIP

    // Naive assumption that jars with no numbers in the name are compiled locally
    } else if (file.name ==~ /.+[^0-9]\.jar/) {
        strategy = ValidationStrategy.COMPILATION

    // client-lib and extensions jars are signed
    } else if (DIGEST_PREFIXES.any { file.path.startsWith(it) }) {
        strategy = ValidationStrategy.CLASS_DIGEST

    // Hash checks for everything else
    } else {
        strategy = ValidationStrategy.JAR_HASH
    }

    jarInTarToStrategyMap[file as String] = strategy
}

def initialFilesSet = jarInTarToStrategyMap.keySet()
def processedFilesSet = new HashSet<String>()

jarInTarToStrategyMap.each {
    file = it.key
    strategy = it.value

    repoPath = tarPathToRepoPath(file)
    println("-- Checking file $file with strategy $strategy")

    if (strategy == ValidationStrategy.SKIP) {
        return
    } else if (strategy == ValidationStrategy.JAR_HASH) {
        handleJarHashStrategy(file, repoPath)
        processedFilesSet.add(file)
    } else if (strategy == ValidationStrategy.CLASS_DIGEST || strategy == ValidationStrategy.COMPILATION) {
        handleCompilationStrategy(file)
        processedFilesSet.add(file)
    }

    return
}

def duration = Duration.between(startInstant, Instant.now())

println()
println("Initial files: ${initialFilesSet.size()}, processed files ${processedFilesSet.size()}")

println("Unprocessed files: ${initialFilesSet - processedFilesSet}")
println("If the unprocessed files list contains install4j jars only then you're most likely good already")
println()


println("Time elapsed: ${duration.toSeconds()} seconds")
