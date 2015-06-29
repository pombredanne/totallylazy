package com.googlecode.totallylazy.proxy;

import com.googlecode.totallylazy.LazyException;
import com.googlecode.totallylazy.Randoms;
import com.googlecode.totallylazy.reflection.Asm;
import com.googlecode.totallylazy.reflection.Reflection;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.numbers.Numbers.sum;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isStatic;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class ProxyBuilder {
    public static final String HANDLER = "handler";
    private static ConcurrentMap<Class<?>, Class<?>> cache = new ConcurrentHashMap<>();

    public static <T> T proxy(Class<T> aClass, InvocationHandler handler) {
        try {
            Class<?> definedClass = cache.computeIfAbsent(aClass, k -> {
                String name = k.getName() + Randoms.integers().head();
                String jvmName = name.replace('.', '/');
                byte[] bytes = bytes(jvmName, k);
                return new DefinableClassLoader().defineClass(name, bytes);
            });

            T instance = Proxy.create(definedClass);
            Field field = definedClass.getDeclaredField(HANDLER);
            field.setAccessible(true);
            field.set(instance, handler);
            return instance;
        } catch (Exception e) {
            throw LazyException.lazyException(e);
        }
    }

    private static class DefinableClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] b){
            return defineClass(name, b, 0, b.length);
        }
    }

    public static byte[] bytes(String name, Class<?> superClass) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String superName = Type.getInternalName(superClass);
        cw.visit(52, ACC_PUBLIC + ACC_SUPER, name, null, superName, null);

        handlerField(cw);

        for (Method method : sequence(superClass.getMethods()).
                reject(m -> isFinal(m.getModifiers())).
                reject(m -> isStatic(m.getModifiers()))) {
            method(cw, name, method);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void handlerField(ClassWriter cw) {
        FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, HANDLER, "Ljava/lang/reflect/InvocationHandler;", null, null);
        fv.visitEnd();
    }

    private static void method(ClassWriter cw, String name, Method method) {
        String[] exceptions = sequence(method.getExceptionTypes()).map(Type::getInternalName).toArray(String.class);
        String methodName = method.getName();
        String methodDescriptor = Type.getMethodDescriptor(method);
        int size = sequence(Type.getArgumentTypes(methodDescriptor)).map(Type::getSize).reduce(sum).intValue() + 1;

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, methodDescriptor, null, exceptions);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitLdcInsn(methodName);
        mv.visitLdcInsn(methodDescriptor);
        mv.visitMethodInsn(INVOKESTATIC, "com/googlecode/totallylazy/reflection/Methods", "method", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/reflect/Method;", false);
        mv.visitVarInsn(ASTORE, size);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, name, HANDLER, "Ljava/lang/reflect/InvocationHandler;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, size);

        int arguments = method.getParameterCount();
        mv.visitLdcInsn(arguments);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

        handleArguments(mv, method);

        mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/reflect/InvocationHandler", "invoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", true);

        handleReturnType(mv, method);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void handleArguments(MethodVisitor mv, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0, local = 0 ; i < parameterTypes.length; i++) {
            local++;
            mv.visitInsn(DUP);
            mv.visitInsn(index(i));
            Class<?> parameterType = parameterTypes[i];
            mv.visitVarInsn(Asm.load(parameterType), local);
            if(parameterType.equals(boolean.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            } else if(parameterType.equals(float.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            } else if(parameterType.equals(double.class)){
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                local++;
            } else if(parameterType.equals(byte.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            } else if(parameterType.equals(short.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            } else if(parameterType.equals(char.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            } else if(parameterType.equals(int.class)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            } else if(parameterType.equals(long.class)){
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                local++;
            }
            mv.visitInsn(AASTORE);
        }
    }

    private static void handleReturnType(MethodVisitor mv, Method method) {
        Class<?> returnType = method.getReturnType();
        if(returnType.equals(void.class)) {
            mv.visitInsn(POP);
        } else if(returnType.isPrimitive()){
            String internalName = Type.getInternalName(Reflection.box(returnType));
            mv.visitTypeInsn(CHECKCAST, internalName);
            mv.visitMethodInsn(INVOKEVIRTUAL, internalName, returnType.getSimpleName() + "Value", "()" + Type.getDescriptor(returnType), false);
        } else {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType));
        }
        mv.visitInsn(Asm.returns(returnType));
    }

    private static int index(int i) {
        if(i == 0) return ICONST_0;
        if(i == 1) return ICONST_1;
        if(i == 2) return ICONST_2;
        if (i == 3) return ICONST_3;
        if(i == 4) return ICONST_4;
        if(i == 5) return ICONST_5;
        throw new UnsupportedOperationException("TODO");
    }

}
