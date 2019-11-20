package cn.sexycode.util.core.object;

import cn.sexycode.util.core.collection.ConcurrentReferenceHashMap;
import cn.sexycode.util.core.exception.AssertionFailure;
import cn.sexycode.util.core.exception.PropertyNotFoundException;
import cn.sexycode.util.core.exception.ReflectException;
import cn.sexycode.util.core.lang.Assert;

import java.beans.Introspector;
import java.lang.reflect.*;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for various reflection operations.
 */
public final class ReflectHelper {

    private static final Pattern JAVA_CONSTANT_PATTERN = Pattern
            .compile("[a-z\\d]+\\.([A-Z]{1}[a-z\\d]+)+\\$?([A-Z]{1}[a-z\\d]+)*\\.[A-Z_\\$]+",
                    Pattern.UNICODE_CHARACTER_CLASS);

    public static final Class[] NO_PARAM_SIGNATURE = new Class[0];

    public static final Object[] NO_PARAMS = new Object[0];

    public static final Class[] SINGLE_OBJECT_PARAM_SIGNATURE = new Class[] { Object.class };

    private static final Method OBJECT_EQUALS;

    private static final Method OBJECT_HASHCODE;

    static {
        Method eq;
        Method hash;
        try {
            eq = extractEqualsMethod(Object.class);
            hash = extractHashCodeMethod(Object.class);
        } catch (Exception e) {
            throw new AssertionFailure("Could not find Object.equals() or Object.hashCode()", e);
        }
        OBJECT_EQUALS = eq;
        OBJECT_HASHCODE = hash;
    }

    /**
     * Disallow instantiation of ReflectHelper.
     */
    private ReflectHelper() {
    }

    public static void ensureAccessibility(AccessibleObject accessibleObject) {
        if ( accessibleObject.isAccessible() ) {
            return;
        }

        accessibleObject.setAccessible( true );
    }
    /**
     * Encapsulation of getting hold of a class's {@link Object#equals equals}  method.
     *
     * @param clazz The class from which to extract the equals method.
     * @return The equals method reference
     * @throws NoSuchMethodException Should indicate an attempt to extract equals method from interface.
     */
    public static Method extractEqualsMethod(Class clazz) throws NoSuchMethodException {
        return clazz.getMethod("equals", SINGLE_OBJECT_PARAM_SIGNATURE);
    }

    /**
     * Encapsulation of getting hold of a class's {@link Object#hashCode hashCode} method.
     *
     * @param clazz The class from which to extract the hashCode method.
     * @return The hashCode method reference
     * @throws NoSuchMethodException Should indicate an attempt to extract hashCode method from interface.
     */
    public static Method extractHashCodeMethod(Class clazz) throws NoSuchMethodException {
        return clazz.getMethod("hashCode", NO_PARAM_SIGNATURE);
    }

    /**
     * Determine if the given class defines an {@link Object#equals} override.
     *
     * @param clazz The class to check
     * @return True if clazz defines an equals override.
     */
    public static boolean overridesEquals(Class clazz) {
        Method equals;
        try {
            equals = extractEqualsMethod(clazz);
        } catch (NoSuchMethodException nsme) {
            return false; //its an interface so we can't really tell anything...
        }
        return !OBJECT_EQUALS.equals(equals);
    }

    /**
     * Determine if the given class defines a {@link Object#hashCode} override.
     *
     * @param clazz The class to check
     * @return True if clazz defines an hashCode override.
     */
    public static boolean overridesHashCode(Class clazz) {
        Method hashCode;
        try {
            hashCode = extractHashCodeMethod(clazz);
        } catch (NoSuchMethodException nsme) {
            return false; //its an interface so we can't really tell anything...
        }
        return !OBJECT_HASHCODE.equals(hashCode);
    }

    /**
     * Determine if the given class implements the given interface.
     *
     * @param clazz The class to check
     * @param intf  The interface to check it against.
     * @return True if the class does implement the interface, false otherwise.
     */
    public static boolean implementsInterface(Class clazz, Class intf) {
        assert intf.isInterface() : "Interface to check was not an interface";
        return intf.isAssignableFrom(clazz);
    }

    /**
     * Perform resolution of a class name.
     * <p/>
     * Here we first check the context classloader, if one, beforeQuery delegating to
     * {@link Class#forName(String, boolean, ClassLoader)} using the caller's classloader
     *
     * @param name   The class name
     * @param caller The class from which this call originated (in order to access that class's loader).
     * @return The class reference.
     * @throws ClassNotFoundException From {@link Class#forName(String, boolean, ClassLoader)}.
     */
    public static Class classForName(String name, Class caller) throws ClassNotFoundException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return classLoader.loadClass(name);
            }
        } catch (Throwable ignore) {
        }
        return Class.forName(name, true, caller.getClassLoader());
    }

    /**
     * Perform resolution of a class name.
     * <p/>
     * Same as {@link #classForName(String, Class)} except that here we delegate to
     * {@link Class#forName(String)} if the context classloader lookup is unsuccessful.
     *
     * @param name The class name
     * @return The class reference.
     * @throws ClassNotFoundException From {@link Class#forName(String)}.
     */
    @Deprecated
    public static Class classForName(String name) throws ClassNotFoundException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return classLoader.loadClass(name);
            }
        } catch (Throwable ignore) {
        }
        return Class.forName(name);
    }

    /**
     * Is this member publicly accessible.
     *
     * @param clazz  The class which defines the member
     * @param member The memeber.
     * @return True if the member is publicly accessible, false otherwise.
     */
    public static boolean isPublic(Class clazz, Member member) {
        return Modifier.isPublic(member.getModifiers()) && Modifier.isPublic(clazz.getModifiers());
    }

    /**
     * Retrieve the default (no arg) constructor from the given class.
     *
     * @param clazz The class for which to retrieve the default ctor.
     * @return The default constructor.
     * @throws PropertyNotFoundException Indicates there was not publicly accessible, no-arg constructor (todo : why PropertyNotFoundException???)
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> clazz) throws PropertyNotFoundException {
        if (isAbstractClass(clazz)) {
            return null;
        }

        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(NO_PARAM_SIGNATURE);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException nme) {
            throw new PropertyNotFoundException(
                    "Object class [" + clazz.getName() + "] must declare a default (no-argument) constructor");
        }
    }

    /**
     * Determine if the given class is declared abstract.
     *
     * @param clazz The class to check.
     * @return True if the class is abstract, false otherwise.
     */
    public static boolean isAbstractClass(Class clazz) {
        int modifier = clazz.getModifiers();
        return Modifier.isAbstract(modifier) || Modifier.isInterface(modifier);
    }

    /**
     * Determine is the given class is declared final.
     *
     * @param clazz The class to check.
     * @return True if the class is final, flase otherwise.
     */
    public static boolean isFinalClass(Class clazz) {
        return Modifier.isFinal(clazz.getModifiers());
    }

    public static Method getMethod(Class clazz, Method method) {
        try {
            return clazz.getMethod(method.getName(), method.getParameterTypes());
        } catch (Exception e) {
            return null;
        }
    }


    private static Field locateField(Class clazz, String propertyName) {
        if (clazz == null || Object.class.equals(clazz)) {
            return null;
        }

        try {
            return clazz.getDeclaredField(propertyName);
        } catch (NoSuchFieldException nsfe) {
            return locateField(clazz.getSuperclass(), propertyName);
        }
    }

    public static Method findGetterMethod(Class containerClass, String propertyName) {
        Class checkClass = containerClass;
        Method getter = null;

        // check containerClass, and then its super types (if any)
        while (getter == null && checkClass != null) {
            if (checkClass.equals(Object.class)) {
                break;
            }

            getter = getGetterOrNull(checkClass, propertyName);
            checkClass = checkClass.getSuperclass();
        }

        // if no getter found yet, check all implemented interfaces
        if (getter == null) {
            getter = getGetterOrNull(containerClass.getInterfaces(), propertyName);
        }

        if (getter == null) {
            throw new PropertyNotFoundException(
                    String.format(Locale.ROOT, "Could not locate getter method for property [%s#%s]",
                            containerClass.getName(), propertyName));
        }

        getter.setAccessible(true);
        return getter;
    }

    private static Method getGetterOrNull(Class[] interfaces, String propertyName) {
        Method getter = null;
        for (int i = 0; getter == null && i < interfaces.length; ++i) {
            final Class anInterface = interfaces[i];
            getter = getGetterOrNull(anInterface, propertyName);
            if (getter == null) {
                // if no getter found yet, check all implemented interfaces of interface
                getter = getGetterOrNull(anInterface.getInterfaces(), propertyName);
            }
        }
        return getter;
    }

    private static Method getGetterOrNull(Class containerClass, String propertyName) {
        for (Method method : containerClass.getDeclaredMethods()) {
            // if the method has parameters, skip it
            if (method.getParameterCount() != 0) {
                continue;
            }

            // if the method is a "bridge", skip it
            if (method.isBridge()) {
                continue;
            }

            final String methodName = method.getName();

            // try "get"
            if (methodName.startsWith("get")) {
                final String stemName = methodName.substring(3);
                final String decapitalizedStemName = Introspector.decapitalize(stemName);
                if (stemName.equals(propertyName) || decapitalizedStemName.equals(propertyName)) {
                    verifyNoIsVariantExists(containerClass, propertyName, method, stemName);
                    return method;
                }

            }

            // if not "get", then try "is"
            if (methodName.startsWith("is")) {
                final String stemName = methodName.substring(2);
                String decapitalizedStemName = Introspector.decapitalize(stemName);
                if (stemName.equals(propertyName) || decapitalizedStemName.equals(propertyName)) {
                    verifyNoGetVariantExists(containerClass, propertyName, method, stemName);
                    return method;
                }
            }
        }

        return null;
    }

    private static void verifyNoIsVariantExists(Class containerClass, String propertyName, Method getMethod,
            String stemName) {
        // verify that the Class does not also define a method with the same stem name with 'is'
        try {
            final Method isMethod = containerClass.getDeclaredMethod("is" + stemName);
            // No such method should throw the caught exception.  So if we get here, there was
            // such a method.
            checkGetAndIsVariants(containerClass, propertyName, getMethod, isMethod);
        } catch (NoSuchMethodException ignore) {
        }
    }

    private static void checkGetAndIsVariants(Class containerClass, String propertyName, Method getMethod,
            Method isMethod) {
        // Check the return types.  If they are the same, its ok.  If they are different
        // we are in a situation where we could not reasonably know which to use.
        if (!isMethod.getReturnType().equals(getMethod.getReturnType())) {
            throw new ReflectException(String.format(Locale.ROOT,
                    "In trying to locate getter for property [%s], Class [%s] defined "
                            + "both a `get` [%s] and `is` [%s] variant", propertyName, containerClass.getName(),
                    getMethod.toString(), isMethod.toString()));
        }
    }

    private static void verifyNoGetVariantExists(Class containerClass, String propertyName, Method isMethod,
            String stemName) {
        // verify that the Class does not also define a method with the same stem name with 'is'
        try {
            final Method getMethod = containerClass.getDeclaredMethod("get" + stemName);
            // No such method should throw the caught exception.  So if we get here, there was
            // such a method.
            checkGetAndIsVariants(containerClass, propertyName, getMethod, isMethod);
        } catch (NoSuchMethodException ignore) {
        }
    }

    public static Method findSetterMethod(Class containerClass, String propertyName, Class propertyType) {
        Class checkClass = containerClass;
        Method setter = null;

        // check containerClass, and then its super types (if any)
        while (setter == null && checkClass != null) {
            if (checkClass.equals(Object.class)) {
                break;
            }

            setter = setterOrNull(checkClass, propertyName, propertyType);
            checkClass = checkClass.getSuperclass();
        }

        // if no setter found yet, check all implemented interfaces
        if (setter == null) {
            setter = setterOrNull(containerClass.getInterfaces(), propertyName, propertyType);
            //			for ( Class theInterface : containerClass.getInterfaces() ) {
            //				setter = setterOrNull( theInterface, propertyName, propertyType );
            //				if ( setter != null ) {
            //					break;
            //				}
            //			}
        }

        if (setter == null) {
            throw new PropertyNotFoundException(
                    String.format(Locale.ROOT, "Could not locate setter method for property [%s#%s]",
                            containerClass.getName(), propertyName));
        }

        setter.setAccessible(true);
        return setter;
    }

    private static Method setterOrNull(Class[] interfaces, String propertyName, Class propertyType) {
        Method setter = null;
        for (int i = 0; setter == null && i < interfaces.length; ++i) {
            final Class anInterface = interfaces[i];
            setter = setterOrNull(anInterface, propertyName, propertyType);
            if (setter == null) {
                // if no setter found yet, check all implemented interfaces of interface
                setter = setterOrNull(anInterface.getInterfaces(), propertyName, propertyType);
            }
        }
        return setter;
    }

    private static Method setterOrNull(Class theClass, String propertyName, Class propertyType) {
        Method potentialSetter = null;

        for (Method method : theClass.getDeclaredMethods()) {
            final String methodName = method.getName();
            if (method.getParameterCount() == 1 && methodName.startsWith("set")) {
                final String testOldMethod = methodName.substring(3);
                final String testStdMethod = Introspector.decapitalize(testOldMethod);
                if (testStdMethod.equals(propertyName) || testOldMethod.equals(propertyName)) {
                    potentialSetter = method;
                    if (propertyType == null || method.getParameterTypes()[0].equals(propertyType)) {
                        break;
                    }
                }
            }
        }

        return potentialSetter;
    }

    /**
     * Pre-built MethodFilter that matches all non-bridge methods.
     *
     * @since 3.0
     * @deprecated as of 5.0.11, in favor of a custom {@link MethodFilter}
     */
    @Deprecated
    public static final MethodFilter NON_BRIDGED_METHODS = (method -> !method.isBridge());

    /**
     * Pre-built MethodFilter that matches all non-bridge non-synthetic methods
     * which are not declared on {@code java.lang.Object}.
     *
     * @since 3.0.5
     */
    public static final MethodFilter USER_DECLARED_METHODS = (method -> (!method.isBridge() && !method.isSynthetic()
            && method.getDeclaringClass() != Object.class));

    /**
     * Pre-built FieldFilter that matches all non-static, non-final fields.
     */
    public static final FieldFilter COPYABLE_FIELDS = field -> !(Modifier.isStatic(field.getModifiers()) || Modifier
            .isFinal(field.getModifiers()));

    /**
     * Naming prefix for CGLIB-renamed methods.
     *
     * @see #isCglibRenamedMethod
     */
    private static final String CGLIB_RENAMED_METHOD_PREFIX = "CGLIB$";

    private static final Method[] NO_METHODS = {};

    private static final Field[] NO_FIELDS = {};

    /**
     * Cache for {@link Class#getDeclaredMethods()} plus equivalent default methods
     * from Java 8 based interfaces, allowing for fast iteration.
     */
    private static final Map<Class<?>, Method[]> declaredMethodsCache = new ConcurrentReferenceHashMap<>(256);

    /**
     * Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration.
     */
    private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);

    /**
     * Attempt to find a {@link Field field} on the supplied {@link Class} with the
     * supplied {@code name}. Searches all superclasses up to {@link Object}.
     *
     * @param clazz the class to introspect
     * @param name  the name of the field
     * @return the corresponding Field object, or {@code null} if not found
     */

    public static Field findField(Class<?> clazz, String name) {
        return findField(clazz, name, null);
    }

    /**
     * Attempt to find a {@link Field field} on the supplied {@link Class} with the
     * supplied {@code name} and/or {@link Class type}. Searches all superclasses
     * up to {@link Object}.
     *
     * @param clazz the class to introspect
     * @param name  the name of the field (may be {@code null} if type is specified)
     * @param type  the type of the field (may be {@code null} if name is specified)
     * @return the corresponding Field object, or {@code null} if not found
     */

    public static Field findField(Class<?> clazz, String name, Class<?> type) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.isTrue(name != null || type != null, "Either name or type of the field must be specified");
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field[] fields = getDeclaredFields(searchType);
            for (Field field : fields) {
                if ((name == null || name.equals(field.getName())) && (type == null || type.equals(field.getType()))) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    /**
     * Set the field represented by the supplied {@link Field field object} on the
     * specified {@link Object target object} to the specified {@code value}.
     * In accordance with {@link Field#set(Object, Object)} semantics, the new value
     * is automatically unwrapped if the underlying field has a primitive type.
     * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
     *
     * @param field  the field to set
     * @param target the target object on which to set the field
     * @param value  the value to set (may be {@code null})
     */
    public static void setField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
            throw new IllegalStateException(
                    "Unexpected reflection exception - " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Get the field represented by the supplied {@link Field field object} on the
     * specified {@link Object target object}. In accordance with {@link Field#get(Object)}
     * semantics, the returned value is automatically wrapped if the underlying field
     * has a primitive type.
     * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
     *
     * @param field  the field to get
     * @param target the target object from which to get the field
     * @return the field's current value
     */

    public static Object getField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
            throw new IllegalStateException(
                    "Unexpected reflection exception - " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Attempt to find a {@link Method} on the supplied class with the supplied name
     * and no parameters. Searches all superclasses up to {@code Object}.
     * <p>Returns {@code null} if no {@link Method} can be found.
     *
     * @param clazz the class to introspect
     * @param name  the name of the method
     * @return the Method object, or {@code null} if none found
     */

    public static Method findMethod(Class<?> clazz, String name) {
        return findMethod(clazz, name, new Class<?>[0]);
    }

    /**
     * Attempt to find a {@link Method} on the supplied class with the supplied name
     * and parameter types. Searches all superclasses up to {@code Object}.
     * <p>Returns {@code null} if no {@link Method} can be found.
     *
     * @param clazz      the class to introspect
     * @param name       the name of the method
     * @param paramTypes the parameter types of the method
     *                   (may be {@code null} to indicate any signature)
     * @return the Method object, or {@code null} if none found
     */

    public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(name, "Method name must not be null");
        Class<?> searchType = clazz;
        while (searchType != null) {
            Method[] methods = (searchType.isInterface() ? searchType.getMethods() : getDeclaredMethods(searchType));
            for (Method method : methods) {
                if (name.equals(method.getName()) && (paramTypes == null || Arrays
                        .equals(paramTypes, method.getParameterTypes()))) {
                    return method;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    /**
     * Invoke the specified {@link Method} against the supplied target object with no arguments.
     * The target object can be {@code null} when invoking a static {@link Method}.
     * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
     *
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @return the invocation result, if any
     * @see #invokeMethod(java.lang.reflect.Method, Object, Object[])
     */

    public static Object invokeMethod(Method method, Object target) {
        return invokeMethod(method, target, new Object[0]);
    }

    /**
     * Invoke the specified {@link Method} against the supplied target object with the
     * supplied arguments. The target object can be {@code null} when invoking a
     * static {@link Method}.
     * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
     *
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @param args   the invocation arguments (may be {@code null})
     * @return the invocation result, if any
     */

    public static Object invokeMethod(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Exception ex) {
            handleReflectionException(ex);
        }
        throw new IllegalStateException("Should never get here");
    }

    /**
     * Invoke the specified JDBC API {@link Method} against the supplied target
     * object with no arguments.
     *
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @return the invocation result, if any
     * @throws SQLException the JDBC API SQLException to rethrow (if any)
     * @see #invokeJdbcMethod(java.lang.reflect.Method, Object, Object[])
     * @deprecated as of 5.0.11, in favor of custom SQLException handling
     */
    @Deprecated

    public static Object invokeJdbcMethod(Method method, Object target) throws SQLException {
        return invokeJdbcMethod(method, target, new Object[0]);
    }

    /**
     * Invoke the specified JDBC API {@link Method} against the supplied target
     * object with the supplied arguments.
     *
     * @param method the method to invoke
     * @param target the target object to invoke the method on
     * @param args   the invocation arguments (may be {@code null})
     * @return the invocation result, if any
     * @throws SQLException the JDBC API SQLException to rethrow (if any)
     * @see #invokeMethod(java.lang.reflect.Method, Object, Object[])
     * @deprecated as of 5.0.11, in favor of custom SQLException handling
     */
    @Deprecated

    public static Object invokeJdbcMethod(Method method, Object target, Object... args) throws SQLException {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof SQLException) {
                throw (SQLException) ex.getTargetException();
            }
            handleInvocationTargetException(ex);
        }
        throw new IllegalStateException("Should never get here");
    }

    /**
     * Handle the given reflection exception. Should only be called if no
     * checked exception is expected to be thrown by the target method.
     * <p>Throws the underlying RuntimeException or Error in case of an
     * InvocationTargetException with such a root cause. Throws an
     * IllegalStateException with an appropriate message or
     * UndeclaredThrowableException otherwise.
     *
     * @param ex the reflection exception to handle
     */
    public static void handleReflectionException(Exception ex) {
        if (ex instanceof NoSuchMethodException) {
            throw new IllegalStateException("Method not found: " + ex.getMessage());
        }
        if (ex instanceof IllegalAccessException) {
            throw new IllegalStateException("Could not access method: " + ex.getMessage());
        }
        if (ex instanceof InvocationTargetException) {
            handleInvocationTargetException((InvocationTargetException) ex);
        }
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    /**
     * Handle the given invocation target exception. Should only be called if no
     * checked exception is expected to be thrown by the target method.
     * <p>Throws the underlying RuntimeException or Error in case of such a root
     * cause. Throws an UndeclaredThrowableException otherwise.
     *
     * @param ex the invocation target exception to handle
     */
    public static void handleInvocationTargetException(InvocationTargetException ex) {
        rethrowRuntimeException(ex.getTargetException());
    }

    /**
     * Rethrow the given {@link Throwable exception}, which is presumably the
     * <em>target exception</em> of an {@link InvocationTargetException}.
     * Should only be called if no checked exception is expected to be thrown
     * by the target method.
     * <p>Rethrows the underlying exception cast to a {@link RuntimeException} or
     * {@link Error} if appropriate; otherwise, throws an
     * {@link UndeclaredThrowableException}.
     *
     * @param ex the exception to rethrow
     * @throws RuntimeException the rethrown exception
     */
    public static void rethrowRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    /**
     * Rethrow the given {@link Throwable exception}, which is presumably the
     * <em>target exception</em> of an {@link InvocationTargetException}.
     * Should only be called if no checked exception is expected to be thrown
     * by the target method.
     * <p>Rethrows the underlying exception cast to an {@link Exception} or
     * {@link Error} if appropriate; otherwise, throws an
     * {@link UndeclaredThrowableException}.
     *
     * @param ex the exception to rethrow
     * @throws Exception the rethrown exception (in case of a checked exception)
     */
    public static void rethrowException(Throwable ex) throws Exception {
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        throw new UndeclaredThrowableException(ex);
    }

    /**
     * Determine whether the given method explicitly declares the given
     * exception or one of its superclasses, which means that an exception
     * of that type can be propagated as-is within a reflective invocation.
     *
     * @param method        the declaring method
     * @param exceptionType the exception to throw
     * @return {@code true} if the exception can be thrown as-is;
     * {@code false} if it needs to be wrapped
     */
    public static boolean declaresException(Method method, Class<?> exceptionType) {
        Assert.notNull(method, "Method must not be null");
        Class<?>[] declaredExceptions = method.getExceptionTypes();
        for (Class<?> declaredException : declaredExceptions) {
            if (declaredException.isAssignableFrom(exceptionType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether the given field is a "public static final" constant.
     *
     * @param field the field to check
     */
    public static boolean isPublicStaticFinal(Field field) {
        int modifiers = field.getModifiers();
        return (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
    }

    /**
     * Determine whether the given method is an "equals" method.
     *
     * @see java.lang.Object#equals(Object)
     */
    public static boolean isEqualsMethod(Method method) {
        if (method == null || !method.getName().equals("equals")) {
            return false;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        return (paramTypes.length == 1 && paramTypes[0] == Object.class);
    }

    /**
     * Determine whether the given method is a "hashCode" method.
     *
     * @see java.lang.Object#hashCode()
     */
    public static boolean isHashCodeMethod(Method method) {
        return (method != null && method.getName().equals("hashCode") && method.getParameterCount() == 0);
    }

    /**
     * Determine whether the given method is a "toString" method.
     *
     * @see java.lang.Object#toString()
     */
    public static boolean isToStringMethod(Method method) {
        return (method != null && method.getName().equals("toString") && method.getParameterCount() == 0);
    }

    /**
     * Determine whether the given method is originally declared by {@link java.lang.Object}.
     */
    public static boolean isObjectMethod(Method method) {
        if (method == null) {
            return false;
        }
        try {
            Object.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Determine whether the given method is a CGLIB 'renamed' method,
     * following the pattern "CGLIB$methodName$0".
     *
     * @param renamedMethod the method to check
     */
    public static boolean isCglibRenamedMethod(Method renamedMethod) {
        String name = renamedMethod.getName();
        if (name.startsWith(CGLIB_RENAMED_METHOD_PREFIX)) {
            int i = name.length() - 1;
            while (i >= 0 && Character.isDigit(name.charAt(i))) {
                i--;
            }
            return ((i > CGLIB_RENAMED_METHOD_PREFIX.length()) && (i < name.length() - 1) && name.charAt(i) == '$');
        }
        return false;
    }

    /**
     * Make the given field accessible, explicitly setting it accessible if
     * necessary. The {@code setAccessible(true)} method is only called
     * when actually necessary, to avoid unnecessary conflicts with a JVM
     * SecurityManager (if active).
     *
     * @param field the field to make accessible
     * @see java.lang.reflect.Field#setAccessible
     */
    @SuppressWarnings("deprecation")  // on JDK 9
    public static void makeAccessible(Field field) {
        if ((!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
                || Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }

    /**
     * Make the given method accessible, explicitly setting it accessible if
     * necessary. The {@code setAccessible(true)} method is only called
     * when actually necessary, to avoid unnecessary conflicts with a JVM
     * SecurityManager (if active).
     *
     * @param method the method to make accessible
     * @see java.lang.reflect.Method#setAccessible
     */
    @SuppressWarnings("deprecation")  // on JDK 9
    public static void makeAccessible(Method method) {
        if ((!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
                && !method.isAccessible()) {
            method.setAccessible(true);
        }
    }

    /**
     * Make the given constructor accessible, explicitly setting it accessible
     * if necessary. The {@code setAccessible(true)} method is only called
     * when actually necessary, to avoid unnecessary conflicts with a JVM
     * SecurityManager (if active).
     *
     * @param ctor the constructor to make accessible
     * @see java.lang.reflect.Constructor#setAccessible
     */
    @SuppressWarnings("deprecation")  // on JDK 9
    public static void makeAccessible(Constructor<?> ctor) {
        if ((!Modifier.isPublic(ctor.getModifiers()) || !Modifier.isPublic(ctor.getDeclaringClass().getModifiers()))
                && !ctor.isAccessible()) {
            ctor.setAccessible(true);
        }
    }

    /**
     * Obtain an accessible constructor for the given class and parameters.
     *
     * @param clazz          the clazz to check
     * @param parameterTypes the parameter types of the desired constructor
     * @return the constructor reference
     * @throws NoSuchMethodException if no such constructor exists
     * @since 5.0
     */
    public static <T> Constructor<T> accessibleConstructor(Class<T> clazz, Class<?>... parameterTypes)
            throws NoSuchMethodException {

        Constructor<T> ctor = clazz.getDeclaredConstructor(parameterTypes);
        makeAccessible(ctor);
        return ctor;
    }

    /**
     * Perform the given callback operation on all matching methods of the given
     * class, as locally declared or equivalent thereof (such as default methods
     * on Java 8 based interfaces that the given class implements).
     *
     * @param clazz the class to introspect
     * @param mc    the callback to invoke for each method
     * @throws IllegalStateException if introspection fails
     * @see #doWithMethods
     * @since 4.2
     */
    public static void doWithLocalMethods(Class<?> clazz, MethodCallback mc) {
        Method[] methods = getDeclaredMethods(clazz);
        for (Method method : methods) {
            try {
                mc.doWith(method);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
            }
        }
    }

    /**
     * Perform the given callback operation on all matching methods of the given
     * class and superclasses.
     * <p>The same named method occurring on subclass and superclass will appear
     * twice, unless excluded by a {@link MethodFilter}.
     *
     * @param clazz the class to introspect
     * @param mc    the callback to invoke for each method
     * @throws IllegalStateException if introspection fails
     * @see #doWithMethods(Class, MethodCallback, MethodFilter)
     */
    public static void doWithMethods(Class<?> clazz, MethodCallback mc) {
        doWithMethods(clazz, mc, null);
    }

    /**
     * Perform the given callback operation on all matching methods of the given
     * class and superclasses (or given interface and super-interfaces).
     * <p>The same named method occurring on subclass and superclass will appear
     * twice, unless excluded by the specified {@link MethodFilter}.
     *
     * @param clazz the class to introspect
     * @param mc    the callback to invoke for each method
     * @param mf    the filter that determines the methods to apply the callback to
     * @throws IllegalStateException if introspection fails
     */
    public static void doWithMethods(Class<?> clazz, MethodCallback mc, MethodFilter mf) {
        // Keep backing up the inheritance hierarchy.
        Method[] methods = getDeclaredMethods(clazz);
        for (Method method : methods) {
            if (mf != null && !mf.matches(method)) {
                continue;
            }
            try {
                mc.doWith(method);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
            }
        }
        if (clazz.getSuperclass() != null) {
            doWithMethods(clazz.getSuperclass(), mc, mf);
        } else if (clazz.isInterface()) {
            for (Class<?> superIfc : clazz.getInterfaces()) {
                doWithMethods(superIfc, mc, mf);
            }
        }
    }

    /**
     * Get all declared methods on the leaf class and all superclasses.
     * Leaf class methods are included first.
     *
     * @param leafClass the class to introspect
     * @throws IllegalStateException if introspection fails
     */
    public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
        final List<Method> methods = new ArrayList<>(32);
        doWithMethods(leafClass, methods::add);
        return methods.toArray(new Method[0]);
    }

    /**
     * Get the unique set of declared methods on the leaf class and all superclasses.
     * Leaf class methods are included first and while traversing the superclass hierarchy
     * any methods found with signatures matching a method already included are filtered out.
     *
     * @param leafClass the class to introspect
     * @throws IllegalStateException if introspection fails
     */
    public static Method[] getUniqueDeclaredMethods(Class<?> leafClass) {
        final List<Method> methods = new ArrayList<>(32);
        doWithMethods(leafClass, method -> {
            boolean knownSignature = false;
            Method methodBeingOverriddenWithCovariantReturnType = null;
            for (Method existingMethod : methods) {
                if (method.getName().equals(existingMethod.getName()) && Arrays
                        .equals(method.getParameterTypes(), existingMethod.getParameterTypes())) {
                    // Is this a covariant return type situation?
                    if (existingMethod.getReturnType() != method.getReturnType() && existingMethod.getReturnType()
                            .isAssignableFrom(method.getReturnType())) {
                        methodBeingOverriddenWithCovariantReturnType = existingMethod;
                    } else {
                        knownSignature = true;
                    }
                    break;
                }
            }
            if (methodBeingOverriddenWithCovariantReturnType != null) {
                methods.remove(methodBeingOverriddenWithCovariantReturnType);
            }
            if (!knownSignature && !isCglibRenamedMethod(method)) {
                methods.add(method);
            }
        });
        return methods.toArray(new Method[0]);
    }

    /**
     * This variant retrieves {@link Class#getDeclaredMethods()} from a local cache
     * in order to avoid the JVM's SecurityManager check and defensive array copying.
     * In addition, it also includes Java 8 default methods from locally implemented
     * interfaces, since those are effectively to be treated just like declared methods.
     *
     * @param clazz the class to introspect
     * @return the cached array of methods
     * @throws IllegalStateException if introspection fails
     * @see Class#getDeclaredMethods()
     */
    private static Method[] getDeclaredMethods(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        Method[] result = declaredMethodsCache.get(clazz);
        if (result == null) {
            try {
                Method[] declaredMethods = clazz.getDeclaredMethods();
                List<Method> defaultMethods = findConcreteMethodsOnInterfaces(clazz);
                if (defaultMethods != null) {
                    result = new Method[declaredMethods.length + defaultMethods.size()];
                    System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
                    int index = declaredMethods.length;
                    for (Method defaultMethod : defaultMethods) {
                        result[index] = defaultMethod;
                        index++;
                    }
                } else {
                    result = declaredMethods;
                }
                declaredMethodsCache.put(clazz, (result.length == 0 ? NO_METHODS : result));
            } catch (Throwable ex) {
                throw new IllegalStateException(
                        "Failed to introspect Class [" + clazz.getName() + "] from ClassLoader [" + clazz
                                .getClassLoader() + "]", ex);
            }
        }
        return result;
    }

    private static List<Method> findConcreteMethodsOnInterfaces(Class<?> clazz) {
        List<Method> result = null;
        for (Class<?> ifc : clazz.getInterfaces()) {
            for (Method ifcMethod : ifc.getMethods()) {
                if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(ifcMethod);
                }
            }
        }
        return result;
    }

    /**
     * Invoke the given callback on all locally declared fields in the given class.
     *
     * @param clazz the target class to analyze
     * @param fc    the callback to invoke for each field
     * @throws IllegalStateException if introspection fails
     * @see #doWithFields
     * @since 4.2
     */
    public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
        for (Field field : getDeclaredFields(clazz)) {
            try {
                fc.doWith(field);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
            }
        }
    }

    /**
     * Invoke the given callback on all fields in the target class, going up the
     * class hierarchy to get all declared fields.
     *
     * @param clazz the target class to analyze
     * @param fc    the callback to invoke for each field
     * @throws IllegalStateException if introspection fails
     */
    public static void doWithFields(Class<?> clazz, FieldCallback fc) {
        doWithFields(clazz, fc, null);
    }

    /**
     * Invoke the given callback on all fields in the target class, going up the
     * class hierarchy to get all declared fields.
     *
     * @param clazz the target class to analyze
     * @param fc    the callback to invoke for each field
     * @param ff    the filter that determines the fields to apply the callback to
     * @throws IllegalStateException if introspection fails
     */
    public static void doWithFields(Class<?> clazz, FieldCallback fc, FieldFilter ff) {
        // Keep backing up the inheritance hierarchy.
        Class<?> targetClass = clazz;
        do {
            Field[] fields = getDeclaredFields(targetClass);
            for (Field field : fields) {
                if (ff != null && !ff.matches(field)) {
                    continue;
                }
                try {
                    fc.doWith(field);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
                }
            }
            targetClass = targetClass.getSuperclass();
        }
        while (targetClass != null && targetClass != Object.class);
    }

    /**
     * This variant retrieves {@link Class#getDeclaredFields()} from a local cache
     * in order to avoid the JVM's SecurityManager check and defensive array copying.
     *
     * @param clazz the class to introspect
     * @return the cached array of fields
     * @throws IllegalStateException if introspection fails
     * @see Class#getDeclaredFields()
     */
    private static Field[] getDeclaredFields(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        Field[] result = declaredFieldsCache.get(clazz);
        if (result == null) {
            try {
                result = clazz.getDeclaredFields();
                declaredFieldsCache.put(clazz, (result.length == 0 ? NO_FIELDS : result));
            } catch (Throwable ex) {
                throw new IllegalStateException(
                        "Failed to introspect Class [" + clazz.getName() + "] from ClassLoader [" + clazz
                                .getClassLoader() + "]", ex);
            }
        }
        return result;
    }

    /**
     * Given the source object and the destination, which must be the same class
     * or a subclass, copy all fields, including inherited fields. Designed to
     * work on objects with public no-arg constructors.
     *
     * @throws IllegalStateException if introspection fails
     */
    public static void shallowCopyFieldState(final Object src, final Object dest) {
        Assert.notNull(src, "Source for field copy cannot be null");
        Assert.notNull(dest, "Destination for field copy cannot be null");
        if (!src.getClass().isAssignableFrom(dest.getClass())) {
            throw new IllegalArgumentException(
                    "Destination class [" + dest.getClass().getName() + "] must be same or subclass as source class ["
                            + src.getClass().getName() + "]");
        }
        doWithFields(src.getClass(), field -> {
            makeAccessible(field);
            Object srcValue = field.get(src);
            field.set(dest, srcValue);
        }, COPYABLE_FIELDS);
    }

    /**
     * Clear the internal method/field cache.
     *
     * @since 4.2.4
     */
    public static void clearCache() {
        declaredMethodsCache.clear();
        declaredFieldsCache.clear();
    }

    /**
     * Action to take on each method.
     */
    @FunctionalInterface
    public interface MethodCallback {

        /**
         * Perform an operation using the given method.
         *
         * @param method the method to operate on
         */
        void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Callback optionally used to filter methods to be operated on by a method callback.
     */
    @FunctionalInterface
    public interface MethodFilter {

        /**
         * Determine whether the given method matches.
         *
         * @param method the method to check
         */
        boolean matches(Method method);
    }

    /**
     * Callback interface invoked on each field in the hierarchy.
     */
    @FunctionalInterface
    public interface FieldCallback {

        /**
         * Perform an operation using the given field.
         *
         * @param field the field to operate on
         */
        void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Callback optionally used to filter fields to be operated on by a field callback.
     */
    @FunctionalInterface
    public interface FieldFilter {

        /**
         * Determine whether the given field matches.
         *
         * @param field the field to check
         */
        boolean matches(Field field);
    }
}
