package ivonhoe.gradle.skin

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import groovy.io.FileType
import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.aapt.SymbolParser
import net.wequick.gradle.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.compile.JavaCompile

/**
 * @author Ivonhoe on 2017/5/31.
 */

public class AaptPlugin implements Plugin<Project> {

    private static final int UNSET_TYPEID = 99
    private static final int UNSET_ENTRYID = -1
    protected static def sPackageIds = [:] as LinkedHashMap<String, Integer>

    protected Set<Project> mDependentLibProjects
    protected Set<Project> mTransitiveDependentLibProjects
    protected Set<Project> mProvidedProjects
    protected Set<Project> mCompiledProjects
    protected Set<Map> mUserLibAars
    protected Set<File> mLibraryJars
    protected File mMinifyJar

    protected Project project
    protected def android
    protected AppExtension andSkin

    @Override
    void apply(Project project) {
        this.andSkin = project.extensions.create('andSkin', AppExtension, project) as AppExtension
        this.project = project
        this.android = project.android

        project.beforeEvaluate {
            beforeEvaluate()
        }

        project.afterEvaluate {
            afterEvaluate()
        }
    }

    protected void beforeEvaluate() {
    }

    protected void afterEvaluate() {
        mUserLibAars = []
        mDependentLibProjects = []
        mProvidedProjects = []
        mCompiledProjects = []

        if (!android.hasProperty('applicationVariants')) {
            return
        }

        android.applicationVariants.all { BaseVariant variant ->
            configureVariant(variant)
        }
    }

    protected void configureVariant(BaseVariant variant) {
        // Init default output file (*.apk)
        andSkin.outputFile = variant.outputs[0].outputFile

        andSkin.buildCaches = new HashMap<String, File>()
        project.tasks.withType(PrepareLibraryTask.class).each {
            TaskUtils.collectAarBuildCacheDir(it, andSkin.buildCaches)
        }

        // Hook variant tasks
        variant.assemble.doLast {
            tidyUp()
        }

        // Fill extensions
        def variantName = variant.name.capitalize()
        File mergerDir = variant.mergeResources.incrementalFolder

        andSkin.with {
            javac = variant.javaCompile
            processManifest = this.project.tasks["process${variantName}Manifest"]

            packageName = variant.applicationId
            packagePath = packageName.replaceAll('\\.', '/')
            classesDir = javac.destinationDir
            bkClassesDir = new File(classesDir.parentFile, "${classesDir.name}~")

            aapt = (ProcessAndroidResources) this.project.tasks["process${variantName}Resources"]
            apFile = aapt.packageOutputFile

            System.out.println("---variant name:" + variantName + ",aapt.textSysmbolOutputDir:" + aapt.textSymbolOutputDir)
            File symbolDir = aapt.textSymbolOutputDir
            File sourceDir = aapt.sourceOutputDir

            symbolFile = new File(symbolDir, 'R.txt')
            rJavaFile = new File(sourceDir, "${packagePath}/R.java")

            splitRJavaFile = new File(sourceDir.parentFile, "andSkin/${packagePath}/R.java")

            mergerXml = new File(mergerDir, 'merger.xml')
        }

        hookVariantTask(variant)
    }

    /** Restore state for DEBUG mode */
    protected void tidyUp() {
    }

    protected void hookVariantTask(BaseVariant variant) {
        hookAapt(andSkin.aapt)

        // Hook clean task to unset package id
        project.clean.doLast {
            sPackageIds.remove(project.name)
        }
    }

    /**
     * Hook aapt task to slice asset package and resolve library resource ids
     */
    private def hookAapt(ProcessAndroidResources aaptTask) {
        aaptTask.doLast { ProcessAndroidResources it ->
            // Unpack resources.ap_
            File apFile = it.packageOutputFile
            FileTree apFiles = project.zipTree(apFile)
            File unzipApDir = new File(apFile.parentFile, 'ap_unzip')
            unzipApDir.delete()

            System.out.println("it.packageOutputFile:" + it.packageOutputFile.absolutePath)

            project.copy {
                from apFiles
                into unzipApDir

                include 'AndroidManifest.xml'
                include 'resources.arsc'
                include 'res/**/*'
            }

            // Modify assets
            prepareSplit()
            // File symbolFile = (andSkin.type == PluginType.Library) ?
            //        new File(it.textSymbolOutputDir, 'R.txt') : null
            File symbolFile = new File(it.textSymbolOutputDir, 'R.txt')

            System.out.println("####symbolFile:" + symbolFile)

            File sourceOutputDir = it.sourceOutputDir
            File rJavaFile = new File(sourceOutputDir, "${andSkin.packagePath}/R.java")

            System.out.println("------it.sourceOutputDir:" + it.sourceOutputDir)
            System.out.println("------rJavaFile:" + andSkin.rJavaFile)
            System.out.println("------splitRJavaFile:" + andSkin.splitRJavaFile)

            def rev = android.buildToolsRevision
            int noResourcesFlag = 0
            def filteredResources = new HashSet()
            def updatedResources = new HashSet()

            // Collect the DynamicRefTable [pkgId => pkgName]
            def libRefTable = [:]
//            libRefTable.put(andSkin.packageId, andSkin.packageName)
//            mTransitiveDependentLibProjects.each {
//                def libAapt = it.tasks.withType(ProcessAndroidResources.class).find {
//                    it.variantName.startsWith('release')
//                }
//                def pkgName = libAapt.packageForR
//                def pkgId = sPackageIds[it.name]
//                libRefTable.put(pkgId, pkgName)
//            }

            System.out.println("***********packageName:" + andSkin.packageName + ",packageId:" + andSkin.packageId)
            Aapt aapt = new Aapt(unzipApDir, rJavaFile, symbolFile, rev)
            if (andSkin.retainedTypes != null && andSkin.retainedTypes.size() > 0) {

                andSkin.retainedTypes.each {
                    //System.out.println("=====retainedTypes:" + it)
                }
                aapt.filterResources(andSkin.retainedTypes, filteredResources)
                Log.success "[${project.name}] split library res files..."

//                andSkin.idStrMaps.each {
//                    System.out.println("idMaps---it:" + it)
//                }
//                System.out.println("andSkin.retainedTypes size:" + andSkin.retainedTypes.size())
//                andSkin.retainedTypes.each {
//                    System.out.println("retained type:" + it)
//                }
                aapt.filterPackage(andSkin.retainedTypes, andSkin.packageId, andSkin.idMaps, libRefTable,
                        andSkin.retainedStyleables, updatedResources)

                Log.success "[${project.name}] slice asset package and reset package id..."

                String pkg = andSkin.packageName
                // Overwrite the aapt-generated R.java with full edition
                aapt.generateRJava(andSkin.rJavaFile, pkg, andSkin.allTypes, andSkin.allStyleables)
                // Also generate a split edition for later re-compiling
                aapt.generateRJava(andSkin.splitRJavaFile, pkg,
                        andSkin.retainedTypes, andSkin.retainedStyleables)

                // Overwrite the retained vendor R.java
                def retainedRFiles = [andSkin.rJavaFile]
                andSkin.vendorTypes.each { name, types ->
                    System.out.println("name:" + name)
                    andSkin.buildCaches.each {
                        //System.out.println("------build cache:" + it)
                    }
                    File aarOutput = andSkin.buildCaches.get(name)
                    if (aarOutput != null) {
                        // TODO: read the aar package name once and store
                        File manifestFile = new File(aarOutput, 'AndroidManifest.xml')
                        def manifest = new XmlParser().parse(manifestFile)
                        String aarPkg = manifest.@package
                        String pkgPath = aarPkg.replaceAll('\\.', '/')
                        File r = new File(sourceOutputDir, "$pkgPath/R.java")
                        retainedRFiles.add(r)

                        def styleables = andSkin.vendorStyleables[name]
                        aapt.generateRJava(r, aarPkg, types, styleables)
                    }
                }

                // Remove unused R.java to fix the reference of shared library resource, issue #63
                sourceOutputDir.eachFileRecurse(FileType.FILES) { file ->
                    if (!retainedRFiles.contains(file)) {
                        file.delete()
                    }
                }

                Log.success "[${project.name}] split library R.java files..."
            } else {
                noResourcesFlag = 1
                if (aapt.deleteResourcesDir(filteredResources)) {
                    Log.success "[${project.name}] remove resources dir..."
                }

                if (aapt.deletePackage(filteredResources)) {
                    Log.success "[${project.name}] remove resources.arsc..."
                }

                if (sourceOutputDir.deleteDir()) {
                    Log.success "[${project.name}] remove R.java..."
                }

                andSkin.symbolFile.delete() // also delete the generated R.txt
            }

            int abiFlag = getABIFlag()
            int flags = (abiFlag << 1) | noResourcesFlag
            if (aapt.writeSmallFlags(flags, updatedResources)) {
                Log.success "[${project.name}] add flags: ${Integer.toBinaryString(flags)}..."
            }

            String aaptExe = andSkin.aapt.buildTools.getPath(BuildToolInfo.PathId.AAPT)

            // Delete filtered entries.
            // Cause there is no `aapt update' command supported, so for the updated resources
            // we also delete first and run `aapt add' later.
            filteredResources.addAll(updatedResources)
            ZipUtils.with(apFile).deleteAll(filteredResources)

            // Re-add updated entries.
            // $ aapt add resources.ap_ file1 file2 ...
            project.exec {
                executable aaptExe
                workingDir unzipApDir
                args 'add', apFile.path
                args updatedResources

                // store the output instead of printing to the console
                standardOutput = new ByteArrayOutputStream()
            }
        }
    }

    protected void prepareSplit() {
        def idsFile = andSkin.symbolFile
        System.out.println("---idsFile:" + idsFile);
        if (!idsFile.exists()) return

        // Check if has any vendor aars
        def firstLevelVendorAars = [] as Set<ResolvedDependency>
        def transitiveVendorAars = [] as Set<Map>
        collectVendorAars(firstLevelVendorAars, transitiveVendorAars)
        if (firstLevelVendorAars.size() > 0) {
            Set<ResolvedDependency> reservedAars = new HashSet<>()
            firstLevelVendorAars.each {
                Log.warn("Using vendor aar '$it.name'")

                // If we don't split the aar then we should reserved all it's transitive aars.
                collectTransitiveAars(it, reservedAars)
            }
            reservedAars.each {
                mUserLibAars.add(group: it.moduleGroup, name: it.moduleName, version: it.moduleVersion)
            }
        }

        andSkin.retainedAars = mUserLibAars

        def vendorTypes = new HashMap<String, List<Map>>()
        def vendorStyleables = [:]
        def vendorEntries = new HashMap<String, HashSet<SymbolParser.Entry>>()

        def intermediatesOutputDir = project.buildDir.getAbsolutePath() + File.separator +
                "intermediates" + File.separator + "exploded-aar" + File.separator
        //获取依赖的aar，及他的资源名称索引
        mUserLibAars.each {
            def group = it.get("group");
            def name = it.get("name");
            def version = it.get("version")
            def key = group + File.separator + name +
                    File.separator + version
            def path = intermediatesOutputDir + key + File.separator + "R.txt"

            def entries = SymbolParser.getResourceEntries(new File(path))
            vendorEntries.put(key, entries)
        }

        File sliceSkinRFile = new File(andSkin.skinSymbols)
        if (!sliceSkinRFile.exists()) {
            throw new RuntimeException(sliceSkinRFile.getAbsolutePath() + ",需要指定正确的R.txt!")
        }

        def sliceEntries = SymbolParser.getResourceEntries(sliceSkinRFile)
        def publicEntries = SymbolParser.getResourceEntries(andSkin.publicSymbolFile)
        def bundleEntries = SymbolParser.getResourceEntries(idsFile)
        def staticIdMaps = [:]
        def staticIdStrMaps = [:]
        def retainedEntries = []
        def retainedPublicEntries = []
        def retainedTypes = []
        def retainedStyleables = []
        def reservedKeys = getReservedResourceKeys()
        def excludeEntries = []

        // Collect all the resources for generating a temporary full edition R.java
        // which required in javac.
        def allTypes = []
        def allStyleables = []
        def addedTypes = [:]

//        sliceEntries.each {
//            System.out.println("需要剥离的类型:" + ",it:" + it)
//        }

        /**
         * 将所有id分为两部分，包含在host里的res和不包换在host里的res
         */
        bundleEntries.each { k, Map be ->
            be._typeId = be.typeId
            be._entryId = be.entryId

            Map le = sliceEntries.get(k)
            if (le == null) {
                be.isStyleable ? retainedStyleables.add(be) : retainedEntries.add(be)
            } else {
                be._typeId = le.typeId
                be._entryId = le.entryId
                be._v = le.id
                be._vs = le.idStr
                System.out.println("------be:" + be)
                System.out.println("------le:" + le)
                excludeEntries.add(be)
            }
        }

        retainedStyleables.each {
            System.out.println("---------retained style:"+it)
        }

        /**
         * 过滤出需要从全部资源列表中分离的资源
         */
        excludeEntries.each {
            staticIdMaps.put(it.id, it._v)
            staticIdStrMaps.put(it.idStr, it._vs)
        }

        /**
         * Prepare public types
         * 将包含在host里的资源，按照type分割，并分配新的id
         */
        def publicTypes = [:]
        retainedEntries.each { e ->
            def type = publicTypes[e.type]
            if (type != null) {
                publicTypes[e.type].add(e)
            } else {
                publicTypes[e.type] = [e]
            }
        }

        def vendorTypesMap = [:]
        // key:aar名称，value:资源列表
        vendorEntries.each { key, vendorEntry ->
            //type:entry
            def map = vendorTypesMap.get(key)
            if (map == null) {
                map = [:]
                vendorTypesMap.put(key, map)
            }

            // vk:type/name vEntry:资源详情
            vendorEntry.each { vk, Map vEntry ->
                if (map.get(vEntry.type) == null) {
                    map.put(vEntry.type, [:])
                }

                map.get(vEntry.type).put(vEntry.key, vEntry)
            }
        }

        publicTypes.each { k, List l ->
            // 根据entryId排序
            l.sort { a, b ->
                return a.entryId.compareTo(b.entryId)
            }

            // 根据保留的entry重新生成新的_id、_entryId、_idStr
            if (l.size() > 0) {
                def currType
                int newEntryId
                l.each { i ->
                    // Prepare entry id maps for resolving resources.arsc and binary xml files
                    if (currType == null || currType.name != i.type) {
                        // New type
                        currType = [type: i.vtype, name: i.type, id: i.typeId, _id: i._typeId, entries: []]
                        retainedTypes.add(currType)
                        newEntryId = 0
                    }

                    i._entryId = newEntryId++
                    def newResId = (andSkin.packageId << 24) | (i._typeId << 16) | i._entryId
                    def newResIdStr = "0x${Integer.toHexString(newResId)}"

                    staticIdMaps.put(i.id, newResId)
                    staticIdStrMaps.put(i.idStr, newResIdStr)

                    // Prepare styleable id maps for resolving R.java
                    if (retainedStyleables.size() > 0 && i.typeId == 1) {
                        retainedStyleables.findAll { it.idStrs != null }.each {
                            // Replace `e.idStr' with `newResIdStr'
                            def index = it.idStrs.indexOf(i.idStr)
                            if (index >= 0) {
                                System.out.println("----it:"+it+",+++++i:"+i)
                                it.idStrs[index] = newResIdStr
                                it.mapped = true
                            }
                        }
                    }

                    def entry = [name: i.key, id: i.entryId, _id: i._entryId, v: i.id, _v: newResId,
                                 vs  : i.idStr, _vs: newResIdStr]
                    currType.entries.add(entry)

                    vendorTypesMap.each { aar, Map typeMap ->
                        List<Map> re = vendorTypes.get(aar)
                        if (re == null) {
                            re = new ArrayList<Map>()
                            vendorTypes.put(aar, re)
                        }

                        typeMap.each { typeName, Map entries ->
                            if (typeName == currType.name && entries.get(entry.name) != null) {
                                boolean found = false
                                re.each { it ->
                                    if (it.name == typeName) {
                                        found = true
                                        it.entries.add(entry)
                                    }
                                }
                                if (!found) {
                                    def vCurrType = [type: currType.type, name: currType.name,
                                                     id  : currType.id, _id: currType._id, entries: []]
                                    vCurrType.entries.add(entry)
                                    re.add(vCurrType)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Update the id array for styleables
        retainedStyleables.findAll { it.mapped != null }.each {
            it.idStr = "{ ${it.idStrs.join(', ')} }"
            it.idStrs = null
        }

        //TODO 需要对typeId和resource id做一个重新排序
        retainedTypes.sort { a, b ->
            return a._id.compareTo(b._id)
        }

        retainedTypes.each {
            def currType = [type: it.type, name: it.name, id: it.id, _id: it._id, entries: it.entries]

            excludeEntries.each { ex ->
                if (ex.type == currType.name) {
                    def entry = [name: ex.key, id: ex.entryId, _id: ex._entryId, v: ex.id, _v: ex._v,
                                 vs  : ex.idStr, _vs: ex._vs]
                    it.entries.add(entry)
                }
            }

//            currType.entries.each {a, b ->
//                return a._id.compareTo(b._id)
//            }
            allTypes.add(currType)
        }

        allStyleables.addAll(retainedStyleables)

        andSkin.idMaps = staticIdMaps
        andSkin.idStrMaps = staticIdStrMaps
        andSkin.retainedTypes = retainedTypes
        andSkin.retainedStyleables = retainedStyleables

        andSkin.allTypes = allTypes
        andSkin.allStyleables = allStyleables

        andSkin.vendorTypes = vendorTypes
        andSkin.vendorStyleables = vendorStyleables
    }

    /** Collect the vendor aars (has resources) compiling in current bundle */
    protected void collectVendorAars(Set<ResolvedDependency> outFirstLevelAars,
                                     Set<Map> outTransitiveAars) {
        project.configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
            collectVendorAars(it, outFirstLevelAars, outTransitiveAars)
        }
    }

    protected boolean collectVendorAars(ResolvedDependency node,
                                        Set<ResolvedDependency> outFirstLevelAars,
                                        Set<Map> outTransitiveAars) {
        def group = node.moduleGroup,
            name = node.moduleName,
            version = node.moduleVersion

        if (group == '' && version == '') {
            // Ignores the dependency of local aar
            return false
        }
        if (andSkin.splitAars.find { aar -> group == aar.group && name == aar.name } != null) {
            // Ignores the dependency which has declared in host or lib.*
            return false
        }
        if (andSkin.retainedAars.find { aar -> group == aar.group && name == aar.name } != null) {
            // Ignores the dependency of normal modules
            return false
        }

        String path = "$group/$name/$version"
        def aar = [path: path, group: group, name: node.name, version: version]
        File aarOutput = andSkin.buildCaches.get(path)
        if (aarOutput != null) {
            def resDir = new File(aarOutput, "res")
            // If the dependency has resources, collect it
            if (resDir.exists() && resDir.list().size() > 0) {
                if (outFirstLevelAars != null && !outFirstLevelAars.contains(node)) {
                    outFirstLevelAars.add(node)
                }
                if (!outTransitiveAars.contains(aar)) {
                    outTransitiveAars.add(aar)
                }
                node.children.each { next ->
                    collectVendorAars(next, null, outTransitiveAars)
                }
                return true
            }
        }

        // Otherwise, check it's children for recursively collecting
        boolean flag = false
        node.children.each { next ->
            flag |= collectVendorAars(next, null, outTransitiveAars)
        }
        if (!flag) return false

        if (outFirstLevelAars != null && !outFirstLevelAars.contains(node)) {
            outFirstLevelAars.add(node)
        }
        return true
    }

    /**
     * Get reserved resource keys of project. For making a smaller slice, the unnecessary
     * resource `mipmap/ic_launcher' and `string/app_name' are excluded.
     */
    protected def getReservedResourceKeys() {
        Set<SymbolParser.Entry> outTypeEntries = new HashSet<>()
        Set<String> outStyleableKeys = new HashSet<>()
        collectReservedResourceKeys(null, null, outTypeEntries, outStyleableKeys)
        def keys = []
        outTypeEntries.each {
            keys.add("$it.type/$it.name")
        }
        outStyleableKeys.each {
            keys.add("styleable/$it")
        }
        return keys
    }

    protected void collectReservedResourceKeys(config, path,
                                               Set<SymbolParser.Entry> outTypeEntries,
                                               Set<String> outStyleableKeys) {
        def merger = new XmlParser().parse(andSkin.mergerXml)
        def filter = config == null ? {
            it.@config == 'main' || it.@config == 'release'
        } : {
            it.@config = config
        }
        def dataSets = merger.dataSet.findAll filter
        dataSets.each { // <dataSet config="main" generated-set="main$Generated">
            it.source.each { // <source path="**/${project.name}/src/main/res">
                if (path != null && it.@path != path) return

                it.file.each {
                    String type = it.@type
                    if (type != null) { // <file name="activity_main" ... type="layout"/>
                        def name = it.@name
                        if (type == 'mipmap'
                                && (name == 'ic_launcher' || name == 'ic_launcher_round')) {
                            // NO NEED IN BUNDLE
                            return
                        }
                        def key = new SymbolParser.Entry(type, name) // layout/activity_main
                        outTypeEntries.add(key)
                        return
                    }

                    it.children().each {
                        type = it.name()
                        String name = it.@name
                        if (type == 'string') {
                            if (name == 'app_name') return // DON'T NEED IN BUNDLE
                        } else if (type == 'style') {
                            name = name.replaceAll("\\.", "_")
                        } else if (type == 'declare-styleable') {
                            // <declare-styleable name="MyTextView">
                            it.children().each { // <attr format="string" name="label"/>
                                def attr = it.@name
                                if (attr.startsWith('android:')) {
                                    attr = attr.replaceAll(':', '_')
                                } else {
                                    def key = new SymbolParser.Entry('attr', attr)
                                    outTypeEntries.add(key)
                                }
                                String key = "${name}_${attr}"
                                outStyleableKeys.add(key)
                            }
                            outStyleableKeys.add(name)
                            return
                        } else if (type.endsWith('-array')) {
                            // string-array or integer-array
                            type = 'array'
                        }

                        def key = new SymbolParser.Entry(type, name)
                        outTypeEntries.add(key)
                    }
                }
            }
        }
    }

    protected int getABIFlag() {
        def abis = []

        def jniDirs = android.sourceSets.main.jniLibs.srcDirs
        if (jniDirs == null) jniDirs = []

        // Collect ABIs from AARs
        def mergeJniLibsTask = project.tasks.withType(TransformTask.class).find {
            it.variantName == 'release' && it.transform.name == 'mergeJniLibs'
        }
        if (mergeJniLibsTask != null) {
            jniDirs.addAll(mergeJniLibsTask.streamInputs.findAll {
                it.isDirectory() && !shouldStripInput(it)
            })
        }

        // Filter ABIs
        def filters = android.defaultConfig.ndkConfig.abiFilters
        jniDirs.each { dir ->
            dir.listFiles().each { File d ->
                if (d.isFile()) return

                def abi = d.name
                if (filters != null && !filters.contains(abi)) return
                if (abis.contains(abi)) return

                abis.add(abi)
            }
        }

        return JNIUtils.getABIFlag(abis)
    }

    protected boolean shouldStripInput(File input) {
        AarPath aarPath = new AarPath(project, input)
        for (aar in andSkin.splitAars) {
            if (aarPath.explodedFromAar(aar)) {
                return true
            }
        }
        return false
    }

    protected void collectTransitiveAars(ResolvedDependency node,
                                         Set<ResolvedDependency> outAars) {
        def group = node.moduleGroup,
            name = node.moduleName

        if (andSkin.splitAars.find { aar -> group == aar.group && name == aar.name } == null) {
            outAars.add(node)
        }

        node.children.each {
            collectTransitiveAars(it, outAars)
        }
    }
}
