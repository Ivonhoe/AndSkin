package ivonhoe.gradle.skin

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

public class RootPlugin implements Plugin<Project> {

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        configureProject()

//        project.task('buildRes') {
//            System.out.println("root-----------buildRes")
//        }
    }

    private void configureProject() {
        project.subprojects {
            System.out.println("it name:" + it.name + ",buildFile:" + it.buildFile.getAbsolutePath())                                                    \

            if (it.name == 'skin-res') {
                it.apply plugin: ResPlugin
            } else if (it.name == 'skin-sample') {
//                it.apply plugin: AaptPlugin
            }
        }
    }
}