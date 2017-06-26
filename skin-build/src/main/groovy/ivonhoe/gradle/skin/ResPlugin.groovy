package ivonhoe.gradle.skin

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import net.wequick.gradle.aapt.SymbolParser
import org.gradle.api.Project

public class ResPlugin extends AaptPlugin {//implements Plugin<Project> {

    private File mBakBuildFile

    protected BaseExtension getAndroid() {
        return project.android
    }

    private boolean isBuildingRes() {
        return project.BUILD_RES
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        andSkin.packageId = 0x79
    }

    protected void configureVariant(BaseVariant variant) {
        if (!isBuildingRes()) {
            return
        }

        super.configureVariant(variant)
    }

    private boolean isBuildResTask() {
        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]

        System.out.println("projectDir:" + p + ",taskName:" + t + ",isBuildingRes：" + isBuildingRes)

        return (t == 'buildRes')
    }

    private void modifyBuildFile() {
        if (!isBuildingRes()) {
            return
        }

        mBakBuildFile = new File(project.buildFile.parentFile, "${project.buildFile.name}~")
        if (mBakBuildFile.exists()) {
            // With `tidyUp', should not reach here
            throw new Exception("Conflict buildFile, please delete file $mBakBuildFile or " + "${project.buildFile}")
        }

        String[] params = new String[2]
        params[0] = 'com\\.android\\.library'
        params[1] = 'com.android.application'

        def text = project.buildFile.text.replaceAll(params[0], params[1])
        project.buildFile.renameTo(mBakBuildFile)
        project.buildFile.write(text)
    }

    protected void tidyUp() {
        // Restore library module's android plugin to `com.android.library'
        if (mBakBuildFile != null && mBakBuildFile.exists()) {
            project.buildFile.delete()
            mBakBuildFile.renameTo(project.buildFile)
        }

        renameToDestination()
    }

    private void renameToDestination() {
        System.out.println("----rename to Destination")
    }

    /**
     * 只是把资源包的resourceId更改成想要的id
     */
    protected void prepareSplit() {
        def idsFile = andSkin.symbolFile
        System.out.println("---idsFile:" + idsFile+",idsFile.exists():"+idsFile.exists());
        if (!idsFile.exists()) return

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
        def vendorTypes = new HashMap<String, List<Map>>()
        def vendorStyleables = [:]

        // 将所有id分为两部分，包含在host里的res和不包换在host里的res
        bundleEntries.each { k, Map be ->
            be._typeId = be.typeId
            be._entryId = be.entryId

            be.isStyleable ? retainedStyleables.add(be) : retainedEntries.add(be)
            System.out.println("------be:" + be)
        }

        // Prepare public types
        // 将包含在host里的资源，按照type分割，并分配新的id
        def publicTypes = [:]
        retainedEntries.each { e ->
            def type = publicTypes[e.type]
            if (type != null) {
                publicTypes[e.type].add(e)
            } else {
                publicTypes[e.type] = [e]
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

                    def entry = [name: i.key, id: i.entryId, _id: i._entryId, v: i.id, _v: newResId,
                                 vs  : i.idStr, _vs: newResIdStr]
                    currType.entries.add(entry)
                }
            }
        }

        retainedTypes.sort { a, b ->
            return a._id.compareTo(b._id)
        }

        andSkin.idMaps = staticIdMaps
        andSkin.idStrMaps = staticIdStrMaps
        andSkin.retainedTypes = retainedTypes
        andSkin.retainedStyleables = retainedStyleables

        andSkin.allTypes = allTypes
        andSkin.allStyleables = allStyleables

        andSkin.vendorTypes = vendorTypes
        andSkin.vendorStyleables = vendorStyleables
    }

}