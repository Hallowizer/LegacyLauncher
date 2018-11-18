package net.minecraft.launchwrapper.injector;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public final class ModuleInjector implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        ClassNode clazz = new ClassNode();
        new ClassReader(basicClass).accept(clazz, ClassReader.SKIP_FRAMES);
        
        for (MethodNode method : clazz.methods)
            for (AbstractInsnNode insn : (Iterable<AbstractInsnNode>) () -> method.instructions.iterator())
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    
                    if (minsn.owner.equals("java/lang/ModuleLayer")) {
                        if (minsn.getOpcode() == Opcodes.INVOKESTATIC) {
                            if (minsn.name.equals("defineModulesWithManyLoaders"))
                                minsn.name = "defineModulesWithOneLoader";
                            
                            if (minsn.name.equals("defineModulesWithOneLoader")) {
                                method.instructions.insertBefore(minsn, new FieldInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/launchwrapper/Launch", "classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;"));
                                method.instructions.insertBefore(minsn, new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/ClassLoader"));
                                
                                InsnList afterList = new InsnList();
                                afterList.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Object"));
                                afterList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/launchwrapper/injector/ModuleInjector", "onModuleLoad", "(Ljava/lang/Object;)Ljava/lang/Object;"));
                                
                                method.instructions.insert(minsn, afterList);
                            }
                        }
                    }
                }
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        clazz.accept(cw);
        return cw.toByteArray();
    }
    
    @SuppressWarnings("unchecked")
    public static Object onModuleLoad(Object controller) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field layerField = controller.getClass().getDeclaredField("layer");
        layerField.setAccessible(true);
        Object layer = layerField.get(controller);
        
        Field nameModuleMap = layer.getClass().getDeclaredField("nameModule");
        nameModuleMap.setAccessible(true);
        Map<String,?> nameToModule = (Map<String, ?>) nameModuleMap.get(layer);
        
        Collection<?> modules = nameToModule.values();
        if (modules.isEmpty())
            return controller;
        
        Class<?> moduleClass = modules.iterator().next().getClass();
        Field loaderField = moduleClass.getDeclaredField("loader");
        loaderField.setAccessible(true);
        
        for (Object module : nameToModule.values()) {
            loaderField.set(module, Launch.classLoader);
            Launch.classLoader.loadModule(module);
        }
        
        return controller;
    }
}
