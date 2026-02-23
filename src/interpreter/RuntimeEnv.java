package interpreter;

import java.util.*;

public final class RuntimeEnv {
    public final Map<String, ClassDef> classes = new HashMap<>();
    public final Deque<Frame> stack = new ArrayDeque<>();

    // execution context for encapsulation + self
    public final Deque<ClassDef> classCtx = new ArrayDeque<>();
    public final Deque<ObjectInstance> selfCtx = new ArrayDeque<>();

    public RuntimeEnv() {
        stack.push(new Frame()); // global frame
    }

    public Frame frame() {
        return stack.peek();
    }

    public void pushFrame(Frame f) {
        stack.push(f);
    }

    public void popFrame() {
        stack.pop();
    }

    public void declareVar(String name, Value init) {
        frame().vars.put(name, init);
    }

    public Value getVar(String name) {
        for (Frame f : stack) {
            if (f.vars.containsKey(name)) return f.vars.get(name);
        }
        throw new RuntimeException("Undefined variable: " + name);
    }

    public boolean isDeclaredSomewhere(String name) {
        for (Frame f : stack) {
            if (f.vars.containsKey(name)) return true;
        }
        return false;
    }

    public void setVar(String name, Value v) {
        for (Frame f : stack) {
            if (f.vars.containsKey(name)) {
                f.vars.put(name, v);
                return;
            }
        }
        throw new RuntimeException("Assignment to undeclared variable: " + name);
    }

    public boolean inClassContext(ClassDef cd) {
        return !classCtx.isEmpty() && classCtx.peek() == cd;
    }

    public ObjectInstance currentSelf() {
        if (selfCtx.isEmpty()) throw new RuntimeException("No 'self' in current context");
        return selfCtx.peek();
    }
}
