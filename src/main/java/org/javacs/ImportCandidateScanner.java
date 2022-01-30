package org.javacs;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class ImportCandidateScanner {
    private ImportCandidateScanner() {}

    static ImportCandidates getJDKClasses() {
        var packages = new HashSet<String>();
        for (var moduleRef : ModuleFinder.ofSystem().findAll()) {
            for (var exports : moduleRef.descriptor().exports()) {
                if (!exports.isQualified()) {
                    packages.add(exports.source());
                }
            }
        }

        var classes = new HashSet<String>();
        var staticImportables = new HashSet<String>();
        try (ScanResult scanResult =
                new ClassGraph()
                        .overrideClassLoaders(ClassLoader.getSystemClassLoader())
                        .ignoreClassVisibility()
                        .ignoreFieldVisibility()
                        .ignoreMethodVisibility()
                        .enableSystemJarsAndModules()
                        .acceptPackagesNonRecursive(packages.toArray(new String[0]))
                        .scan()) {
            for (ClassInfo ci : scanResult.getAllClasses()) {
                Visibility visibility = Visibility.ofClass(ci);
                if (visibility == null) {
                    // Private. Not visible.
                    continue;
                }

                indexClass(classes, ci);
                for (var mi : ci.getDeclaredMethodAndConstructorInfo()) {
                    indexMethod(staticImportables, mi);
                }
                for (var fi : ci.getDeclaredFieldInfo()) {
                    indexField(staticImportables, fi);
                }
            }
        }
        return new ImportCandidates(classes, staticImportables);
    }

    static ImportCandidates getClasses(Set<Path> classPath) {
        var urls = classPath.stream().map(ImportCandidateScanner::toUrl).toArray(URL[]::new);
        var classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());

        var classes = new HashSet<String>();
        var staticImportables = new HashSet<String>();
        try (ScanResult scanResult =
                new ClassGraph()
                        .overrideClassLoaders(classLoader)
                        .ignoreClassVisibility()
                        .ignoreFieldVisibility()
                        .ignoreMethodVisibility()
                        .enableAllInfo()
                        .scan()) {
            for (ClassInfo ci : scanResult.getAllClasses()) {
                Visibility visibility = Visibility.ofClass(ci);
                if (visibility == null) {
                    // Private. Not visible.
                    continue;
                }

                indexClass(classes, ci);
                for (var mi : ci.getDeclaredMethodAndConstructorInfo()) {
                    indexMethod(staticImportables, mi);
                }
                for (var fi : ci.getDeclaredFieldInfo()) {
                    indexField(staticImportables, fi);
                }
            }
        }
        return new ImportCandidates(classes, staticImportables);
    }

    private static URL toUrl(Path p) {
        try {
            return p.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void indexClass(Set<String> classes, ClassInfo ci) {
        String name = ci.getSimpleName();
        String packageName = ci.getPackageName();
        String containedClass = getMiddle(ci.getName().replace('$', '.'), packageName, name);
        classes.add(buildFQN(packageName, containedClass, name));
    }

    private static void indexMethod(Set<String> staticImportables, MethodInfo mi) {
        if (!mi.isStatic()) {
            return;
        }
        Visibility visibility = Visibility.ofMethod(mi);
        if (visibility == null) {
            return;
        }

        String name = mi.getName();
        String packageName = mi.getClassInfo().getPackageName();
        String containedClass = getMiddle(mi.getClassName().replace('$', '.') + '.' + name, packageName, name);
        staticImportables.add(buildFQN(packageName, containedClass, name));
    }

    private static void indexField(Set<String> staticImportables, FieldInfo fi) {
        if (!fi.isStatic()) {
            return;
        }
        Visibility visibility = Visibility.ofField(fi);
        if (visibility == null) {
            return;
        }

        String name = fi.getName();
        String packageName = fi.getClassInfo().getPackageName();
        String containedClass = getMiddle(fi.getClassName().replace('$', '.') + '.' + name, packageName, name);
        staticImportables.add(buildFQN(packageName, containedClass, name));
    }

    private static String buildFQN(String packageName, String containedClass, String name) {
        String fqn = packageName;
        if (!containedClass.isEmpty()) {
            fqn += "." + containedClass;
        }
        if (fqn.isEmpty()) {
            fqn = name;
        } else {
            fqn += "." + name;
        }
        return fqn;
    }

    private static String getMiddle(String fullName, String packageName, String simpleName) {
        String s = fullName;
        if (!packageName.isEmpty()) {
            s = s.substring(packageName.length() + 1);
        }
        if (s.equals(simpleName)) {
            return "";
        }
        return s.substring(0, s.length() - simpleName.length() - 1);
    }

    static class ImportCandidates {
        final Set<String> classes;
        final Set<String> staticImportables;

        ImportCandidates(Set<String> classes, Set<String> staticImportables) {
            this.classes = classes;
            this.staticImportables = staticImportables;
        }
    }

    private enum Visibility {
        PUBLIC,
        PACKAGE;

        static Visibility ofClass(ClassInfo ci) {
            if (ci.isPublic()) {
                return Visibility.PUBLIC;
            }
            if (ci.isPrivate()) {
                return null;
            }
            // Protected visibility is package private.
            return Visibility.PACKAGE;
        }

        static Visibility ofMethod(MethodInfo mi) {
            if (mi.isPublic()) {
                return Visibility.PUBLIC;
            }
            if (mi.isPrivate()) {
                return null;
            }
            // Protected visibility is package private.
            return Visibility.PACKAGE;
        }

        static Visibility ofField(FieldInfo fi) {
            if (fi.isPublic()) {
                return Visibility.PUBLIC;
            }
            if (fi.isPrivate()) {
                return null;
            }
            // Protected visibility is package private.
            return Visibility.PACKAGE;
        }
    }
}
