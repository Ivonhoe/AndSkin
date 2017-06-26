android {
    libraryVariants.all { variant ->
        variant.outputs.each { output ->
            def outputFile = output.outputFile
            if (outputFile != null && outputFile.name.endsWith('.aar')) {
                def fileName = "${archivesBaseName}-${version}.aar"
                output.outputFile = new File(outputFile.parent, fileName)
            }
        }
    }

    lintOptions {
        abortOnError false
    }
}
/**
 * 拷贝编译生成的jar到指定目录
 */
def copyAar = {
    String projectName = project.getName();
    String groupId = project.getGroup();
    groupId = groupId.replace('.', '/')
    String versionName = project.getVersion();
    String outputFile = "build/outputs/aar/" + projectName + '-' + versionName + '.aar'
    String targetDir = '/.m2/repository/' + groupId + '/' + projectName + '/' + versionName + '/'
    String[] dirs = rootDir.absolutePath.split(File.separator)

    String targetRoot = null;
    if (dirs[1].equals('Users') && dirs.length > 2) {
        targetRoot = '/Users/' + dirs[2]
    }

    if (targetRoot == null) {
        throw new Exception("Get root dir and user name failed!")
    }

    targetDir = targetRoot + targetDir;
    copy {
        from outputFile
        into targetDir
    }
}

task copy2Local(dependsOn: "build") {

    doLast {
        copyAar()
    }
}