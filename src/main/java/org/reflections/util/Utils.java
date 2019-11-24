package org.reflections.util;

import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.reflections.ReflectionUtils.forName;

/**
 * a garbage can of convenient methods
 */
public abstract class Utils {

    public static String repeat(String string, int times) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < times; i++) {
            sb.append(string);
        }

        return sb.toString();
    }

    /**
     * isEmpty compatible with Java 5
     */
    public static boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean isEmpty(Object[] objects) {
        return objects == null || objects.length == 0;
    }

    public static File prepareFile(String filename) {
        File file = new File(filename);
        File parent = file.getAbsoluteFile().getParentFile();
        if (!parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        return file;
    }

    public static Member getMemberFromDescriptor(String descriptor, ClassLoader... classLoaders) throws ReflectionsException {
        int p0 = descriptor.lastIndexOf('(');
        String memberKey = p0 != -1 ? descriptor.substring(0, p0) : descriptor;
        String methodParameters = p0 != -1 ? descriptor.substring(p0 + 1, descriptor.lastIndexOf(')')) : "";

        int p1 = Math.max(memberKey.lastIndexOf('.'), memberKey.lastIndexOf("$"));
        String className = memberKey.substring(memberKey.lastIndexOf(' ') + 1, p1);
        String memberName = memberKey.substring(p1 + 1);

        Class<?>[] parameterTypes = null;
        if (!isEmpty(methodParameters)) {
            String[] parameterNames = methodParameters.split(",");
            List<Class<?>> result = new ArrayList<Class<?>>(parameterNames.length);
            for (String name : parameterNames) {
                result.add(forName(name.trim(), classLoaders));
            }
            parameterTypes = result.toArray(new Class<?>[result.size()]);
        }

        Class<?> aClass = forName(className, classLoaders);
        while (aClass != null) {
            try {
                if (!descriptor.contains("(")) {
                    return aClass.isInterface() ? aClass.getField(memberName) : aClass.getDeclaredField(memberName);
                } else if (isConstructor(descriptor)) {
                    return aClass.isInterface() ? aClass.getConstructor(parameterTypes) : aClass.getDeclaredConstructor(parameterTypes);
                } else {
                    return aClass.isInterface() ? aClass.getMethod(memberName, parameterTypes) : aClass.getDeclaredMethod(memberName, parameterTypes);
                }
            } catch (Exception e) {
                aClass = aClass.getSuperclass();
            }
        }
        throw new ReflectionsException("Can't resolve member named " + memberName + " for class " + className);
    }

    public static Set<Method> getMethodsFromDescriptors(Iterable<String> annotatedWith, ClassLoader... classLoaders) {
        Set<Method> result = new HashSet<>();
        for (String annotated : annotatedWith) {
            if (!isConstructor(annotated)) {
                Method member = (Method) getMemberFromDescriptor(annotated, classLoaders);
                if (member != null) result.add(member);
            }
        }
        return result;
    }

    public static Set<Constructor> getConstructorsFromDescriptors(Iterable<String> annotatedWith, ClassLoader... classLoaders) {
        Set<Constructor> result = new HashSet<>();
        for (String annotated : annotatedWith) {
            if (isConstructor(annotated)) {
                Constructor member = (Constructor) getMemberFromDescriptor(annotated, classLoaders);
                if (member != null) result.add(member);
            }
        }
        return result;
    }

    public static Set<Member> getMembersFromDescriptors(Iterable<String> values, ClassLoader... classLoaders) {
        Set<Member> result = new HashSet<>();
        for (String value : values) {
            try {
                result.add(Utils.getMemberFromDescriptor(value, classLoaders));
            } catch (ReflectionsException e) {
                throw new ReflectionsException("Can't resolve member named " + value, e);
            }
        }
        return result;
    }

    public static Field getFieldFromString(String field, ClassLoader... classLoaders) {
        String className = field.substring(0, field.lastIndexOf('.'));
        String fieldName = field.substring(field.lastIndexOf('.') + 1);

        try {
            return forName(className, classLoaders).getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new ReflectionsException("Can't resolve field named " + fieldName, e);
        }
    }

    public static void close(InputStream closeable) {
        try { if (closeable != null) closeable.close(); }
        catch (IOException e) {
            if (Reflections.log != null) {
                Reflections.log.warn("Could not close InputStream", e);
            }
        }
    }

    @Nullable
    public static Logger findLogger(Class<?> aClass) {
        try {
            // This is to check whether an optional SLF4J binding is available. While SLF4J recommends that libraries
            // "should not declare a dependency on any SLF4J binding but only depend on slf4j-api", doing so forces
            // users of the library to either add a binding to the classpath (even if just slf4j-nop) or to set the
            // "slf4j.suppressInitError" system property in order to avoid the warning, which both is inconvenient.
            Class.forName("org.slf4j.impl.StaticLoggerBinder");
            return LoggerFactory.getLogger(aClass);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean isConstructor(String fqn) {
        return fqn.contains("init>");
    }

    public static String name(Class type) {
        if (!type.isArray()) {
            return type.getName();
        } else {
            int dim = 0;
            while (type.isArray()) {
                dim++;
                type = type.getComponentType();
            }
            return type.getName() + repeat("[]", dim);
        }
    }


    public static List<String> names(Iterable<Class<?>> types) {
        List<String> result = new ArrayList<String>();
        for (Class<?> type : types) result.add(name(type));
        return result;
    }

    public static List<String> names(Class<?>... types) {
        return names(Arrays.asList(types));
    }

    public static String name(Constructor constructor) {
        return constructor.getName() + "." + "<init>" + "(" + String.join(", ", names(constructor.getParameterTypes())) + ")";
    }

    public static String name(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName() + "(" + String.join(", ", names(method.getParameterTypes())) + ")";
    }

    public static String name(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    public static String index(Class<? extends Scanner> scannerClass) { return scannerClass.getSimpleName(); }

    public static <T> T[] concatArrays(T[] first, T[] second, T[] target) {
        System.arraycopy(first, 0, target, 0, first.length);
        System.arraycopy(second, 0, target, first.length, second.length);
        return target;
    }

    public static <T> Iterable<T> concat(final List<Iterable<T>> chain) {
        if(chain == null) {
            return Collections.emptyList();
        }
        return chain.stream().filter(Objects::nonNull).map(el -> StreamSupport.stream(el.spliterator(), false))
                .flatMap(s->s).collect(Collectors.toList());
    }

    public static <T> List<T> concat(final List<T> elements1, final Collection<T> elements2) {
        final ArrayList<T> list = new ArrayList<>(elements1);
        list.addAll(elements2);
        return list;
    }

    public static <T> Stream<T> asStream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static <T> List<T> asList(final Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }

    public static <T> List<T> concatIterables(final Iterable<T> elements1, final Iterable<T> elements2) {
        return concat(asList(elements1) , asList(elements2));
    }
}
