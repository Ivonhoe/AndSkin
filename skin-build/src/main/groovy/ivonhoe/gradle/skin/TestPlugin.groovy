package ivonhoe.gradle.skin

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

public class TestPlugin implements Plugin<Project> {

    private Project project
    private boolean isBuildingRes

    @Override
    void apply(Project project) {
        this.project = project

        System.out.println("------------TestPlugin plugin apply")
    }

    private void configureProject() {
        project.subprojects {
            System.out.println("it name:" + it.name + ",buildFile:" + it.buildFile.getAbsolutePath())                               \

            if (it.name == 'skin-res') {
                String[] params = new String[2]
                params[0] = 'com\\.android\\.library'
                params[1] = 'com.android.application'

                File bakBuildFile = new File(it.buildFile.parentFile, "${it.buildFile.name}~")
                def text = it.buildFile.text.replaceAll(params[0], params[1])
//                it.buildFile.renameTo(bakBuildFile)
//                it.buildFile.write(text)
                System.out.println("------------root configure project")
                it.apply plugin: ResPlugin
            } else if (it.name == 'skin-sample') {
                it.apply plugin: AaptPlugin
            }
        }
    }

    private boolean isBuildResTask() {
        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]


        return (t == 'buildRes')
    }

    private void beforeEvaluate() {
        if (!isBuildingRes) {
            return
        }

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

    private void afterEvaluate() {
        System.out.println("--------afterEvaluate")
        if (!isBuildingRes) {
            return
        }

        android = this.project.android
        android.applicationVariants.all { BaseVariant variant ->
            configureVariant(variant)
        }
    }

    private void configureVariant(BaseVariant variant) {
        // Hook variant tasks
        variant.assemble.doLast {
            tidyUp()
        }
    }

    private void tidyUp() {
        // Restore library module's android plugin to `com.android.library'
        if (mBakBuildFile != null && mBakBuildFile.exists()) {
            project.buildFile.delete()
            mBakBuildFile.renameTo(project.buildFile)
        }
    }

}