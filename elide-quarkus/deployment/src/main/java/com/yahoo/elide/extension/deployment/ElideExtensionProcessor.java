package com.yahoo.elide.extension.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;
import com.yahoo.elide.annotation.Include;
import com.yahoo.elide.annotation.LifeCycleHookBinding;
import com.yahoo.elide.annotation.SecurityCheck;
import com.yahoo.elide.core.utils.ClassScanner;
import com.yahoo.elide.core.utils.coerce.converters.ElideTypeConverter;
import com.yahoo.elide.extension.runtime.ElideConfig;
import com.yahoo.elide.extension.runtime.ElideRecorder;
import com.yahoo.elide.jsonapi.resources.JsonApiEndpoint;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyIgnoreWarningBuildItem;
import io.quarkus.hibernate.orm.deployment.JpaModelIndexBuildItem;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.inject.Default;
import javax.inject.Singleton;

class ElideExtensionProcessor {

    private static final String FEATURE = "elide-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public AdditionalBeanBuildItem elideEndpoints( ) {
        return AdditionalBeanBuildItem.builder().addBeanClass(JsonApiEndpoint.class).build();
    }

    @Record(STATIC_INIT)
    @BuildStep
    void build(ElideConfig elideConfig,
               ElideRecorder recorder,
               BuildProducer<BeanContainerListenerBuildItem> containerListenerProducer) {

        containerListenerProducer.produce(
                new BeanContainerListenerBuildItem(recorder.setLiquibaseConfig(elideConfig)));
    }

    @BuildStep
    List<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotation() {
        List<BeanDefiningAnnotationBuildItem> additionalBeanAnnotations = new ArrayList<>();
        additionalBeanAnnotations.add(
                new BeanDefiningAnnotationBuildItem(DotName.createSimple(Include.class.getCanonicalName())));
        additionalBeanAnnotations.add(
                new BeanDefiningAnnotationBuildItem(DotName.createSimple(SecurityCheck.class.getCanonicalName())));
        additionalBeanAnnotations.add(
                new BeanDefiningAnnotationBuildItem(DotName.createSimple(LifeCycleHookBinding.class.getCanonicalName())));
        additionalBeanAnnotations.add(
                new BeanDefiningAnnotationBuildItem(DotName.createSimple(ElideTypeConverter.class.getCanonicalName())));

        return additionalBeanAnnotations;
    }

    @BuildStep
    @Record(STATIC_INIT)
    public List<ReflectiveHierarchyIgnoreWarningBuildItem> elideModels(
            JpaModelIndexBuildItem index,
            ElideRecorder elideRecorder,
            BuildProducer<SyntheticBeanBuildItem> synthenticBean
    ) {
        List<ReflectiveHierarchyIgnoreWarningBuildItem> reflectionBuildItems = new ArrayList<>();
        List<Class<?>> elideClasses = new ArrayList<>();

        index.getIndex().getKnownClasses().forEach(classInfo -> {
            boolean found = false;

            for (Class annotationClass : ElideRecorder.ANNOTATIONS) {
                AnnotationInstance instance =
                        classInfo.classAnnotation(DotName.createSimple(annotationClass.getSimpleName()));

                if (instance != null) {
                    found = true;
                    break;
                }
            }

            if (found) {
                try {
                    Class<?> beanClass = Class.forName(classInfo.name().toString(), false,
                            Thread.currentThread().getContextClassLoader());

                    elideClasses.add(beanClass);
                } catch (ClassNotFoundException e) {
                    //TODO - logging
                }
                reflectionBuildItems.add(new ReflectiveHierarchyIgnoreWarningBuildItem(classInfo.name()));
            }
        });

        synthenticBean.produce(SyntheticBeanBuildItem.configure(ClassScanner.class).scope(Singleton.class)
                .supplier(elideRecorder.createClassScanner(elideClasses))
                .unremovable()
                .addQualifier(Default.class)
                .done());

        return reflectionBuildItems;
    }
}