package com.vladmihalcea.phd.metrics;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.b333vv.metric.export.CsvPackageMetricsExporter;
import org.b333vv.metric.model.code.ClassElement;
import org.b333vv.metric.model.code.PackageElement;
import org.b333vv.metric.model.code.ProjectElement;
import org.b333vv.metric.model.metric.Metric;
import org.b333vv.metric.model.metric.MetricType;
import org.b333vv.metric.service.CalculationService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Exports the package metrics of one quality-metrics module with MetricsTree, as its
 * "Export Package Metrics to CSV" action does in the IDE.
 * <p>
 * The module sources, its compile-scope dependency jars, and the JDK come from system properties,
 * so that the sources resolve against the same types as in the IDE: the efferent coupling counts the
 * packages of the resolved types, the JDK and library packages included.
 * <p>
 * Besides the MetricsTree export ({@code <output>}), the test writes the sum of the cyclomatic complexities
 * of the methods of each package ({@code <output>} with the {@code -cc.csv} suffix), which the export
 * omits although the package Maintainability Index uses it.
 */
public class MetricsTreePackageExportTest extends LightJavaCodeInsightFixtureTestCase {

    private static final Path SOURCES = requiredPath("metricstree.sources");
    private static final Path DEPENDENCIES = requiredPath("metricstree.dependencies");
    private static final Path JDK = requiredPath("metricstree.jdk");
    private static final Path OUTPUT = requiredPath("metricstree.output");

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    @Override
    protected String getTestDataPath() {
        return SOURCES.toString();
    }

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return new DefaultLightProjectDescriptor(() -> JavaSdk.getInstance().createJdk("analysis-jdk", JDK.toString(), false)) {
            @Override
            public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
                super.configureModule(module, model, contentEntry);
                String[] jars = dependencyJars();
                if (jars.length > 0) {
                    PsiTestUtil.addLibrary(model, "module-dependencies", DEPENDENCIES.toString(), jars);
                }
            }
        };
    }

    private static String[] dependencyJars() {
        if (!Files.isDirectory(DEPENDENCIES)) {
            return new String[0];
        }
        try (Stream<Path> files = Files.list(DEPENDENCIES)) {
            return files
                .map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".jar"))
                .sorted()
                .toArray(String[]::new);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void testExportPackageMetrics() throws IOException {
        myFixture.copyDirectoryToProject("", "");
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());

        // The same model as the export action: class and method metrics, dependencies, package metrics,
        // and project metrics, over the production sources of the project. In the IDE, the action runs
        // in a background task, whose progress indicator the project-level calculator asks for.
        ProgressIndicator indicator = new EmptyProgressIndicator();
        ProjectElement projectElement = ProgressManager.getInstance().runProcess(
            () -> getProject().getService(CalculationService.class).getOrBuildProjectMetricsModel(indicator, null),
            indicator);

        Files.createDirectories(OUTPUT.getParent());
        new CsvPackageMetricsExporter(getProject()).export(OUTPUT.toString(), projectElement);
        assertTrue("MetricsTree wrote no " + OUTPUT, Files.exists(OUTPUT));

        writeCyclomaticComplexity(projectElement);
    }

    /**
     * Sums the cyclomatic complexity of the methods of each package, as the package Maintainability
     * Index of MetricsTree does.
     */
    private void writeCyclomaticComplexity(ProjectElement projectElement) throws IOException {
        String fileName = OUTPUT.getFileName().toString().replaceFirst("\\.csv$", "") + "-cc.csv";
        Path output = OUTPUT.resolveSibling(fileName);
        List<PackageElement> packages = projectElement.allPackages()
            .filter(p -> p.getPsiPackage() != null)
            .sorted(Comparator.comparing(p -> p.getPsiPackage().getQualifiedName()))
            .toList();
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.println("Package Name;CC");
            for (PackageElement packageElement : packages) {
                long cc = packageElement.classes()
                    .flatMap(ClassElement::methods)
                    .flatMap(method -> method.metrics())
                    .filter(metric -> metric.getType() == MetricType.CC)
                    .map(Metric::getValue)
                    .filter(Objects::nonNull)
                    .mapToLong(Number::longValue)
                    .sum();
                writer.println(packageElement.getPsiPackage().getQualifiedName() + ";" + cc);
            }
        }
    }
}
