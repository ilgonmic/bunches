package org.jetbrains.bunches.hooks

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.CommitInfo
import java.io.File
import kotlin.system.exitProcess

fun getBunchExtensions(dotBunchFile: File): Set<String>? {
    val lines = dotBunchFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size <= 1) return null

    return lines.drop(1).map { it.split('_').first() }.toSet()
}

fun getExtensions(): Set<String> {
    val dotBunchFile = File(".bunch")
    if (!dotBunchFile.exists()) {
        println("Project's .bunch file wasn't found, hook is disabled")
        exitProcess(0)
    }

    return getBunchExtensions(dotBunchFile) ?: emptySet()
}

fun fileWithoutExtension(filename: String): String {
    return filename.removeSuffix(".${File(filename).extension}")
}

fun checkCommitInterval(commits: List<CommitInfo>): String {
    var message = ""

    val extensions = getExtensions()
    for (commit in commits) {
        for (action in commit.fileActions) {
            if (action.changeType == DiffEntry.ChangeType.ADD
                && File(action.newPath ?: continue).extension in extensions
            ) {
                if (isDeleted(action.newPath, commits.reversed().dropWhile { it != commit })) {
                    continue
                }
                val mainFile = fileWithoutExtension(action.newPath)
                val danger = commits.reversed().dropWhile { it != commit }
                    .filter {
                        it.fileActions.any { current ->
                            current.newPath == mainFile
                        }
                    }
                    .filter {
                        it.fileActions.all { current ->
                            current.newPath != action.newPath
                        }
                    }

                if (danger.isNotEmpty()) {
                    message += "Affected $mainFile, but didnt ${action.newPath}:\n${danger.joinToString(",\n") { "${it.title} ${it.hash}" }}\n"
                }
            }
        }
    }

    val files = commits.map { it.fileActions.map { action -> action.newPath } }.flatten().filterNotNull()
    for (file in files) {
        if (!isCreated(file, commits) && !isDeleted(file, commits) && File(file).extension in extensions) {
            val mainFile = fileWithoutExtension(file)
            val danger = commits.reversed()
                .filter { it.fileActions.any { current -> current.newPath == mainFile } }
                .filter { it.fileActions.all { current -> current.newPath != file } }

            if (danger.isNotEmpty()) {
                message += "Affected $mainFile, but didnt $file:\n${danger.joinToString(",\n") { "${it.title} ${it.hash}" }}\n"
            }
        }
    }
    return message
}

fun findCommitWithType(file: String, commits: List<CommitInfo>, type: DiffEntry.ChangeType): CommitInfo? {
    return commits.firstOrNull {
        it.fileActions.any { action -> action.changeType == type && action.newPath == file }
    }
}

fun findFirstCommit(file: String, commits: List<CommitInfo>): CommitInfo? {
    return findCommitWithType(file, commits, DiffEntry.ChangeType.ADD)
}

fun isCreated(filePath: String, commits: List<CommitInfo>): Boolean {
    return findFirstCommit(filePath, commits) != null
}

fun isDeleted(filePath: String, commits: List<CommitInfo>): Boolean {
    return findCommitWithType(filePath, commits, DiffEntry.ChangeType.DELETE) != null
}