package interpreter;

import java.util.*;

public final class RuntimeEnv {
    public final Map<String, ClassDef> classes = new HashMap<>();
    public final Deque<Frame> stack = new ArrayDeque<>();

    // current "self" and current class context (for private checks)
    public final Deque<ObjectInstance> selfStack = new ArrayDeque<>();
    public final Deque<ClassDef> classCtxStack = new ArrayDeque<>();

    public RuntimeEnv() {
        stack.push(new Frame()); // global frame
    }

    public Frame frame() { return stack.peek(); }

    public Value getVar(String name) {
        for (Frame f : stack) {
            if (f.vars.containsKey(name)) return f.vars.get(name);
        }
        throw new RuntimeException("Undefined variable: " + name);
    }

    public void setVar(String name, Value v) {
        for (Frame f : stack) {
            if (f.vars.containsKey(name)) { f.vars.put(name, v); return; }
        }
        // if not declared, allow implicit global set? better to error:
        throw new RuntimeException("Assignment to undeclared variable: " + name);
    }

    public void declareVar(String name, Value v) {
        frame().vars.put(name, v);
    }

    public boolean inClassContext(ClassDef cd) {
        return !classCtxStack.isEmpty() && classCtxStack.peek() == cd;
    }
}
