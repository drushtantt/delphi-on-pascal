package interpreter;

import java.util.*;

public final class ClassDef {
    public enum Vis { PUBLIC, PRIVATE, PROTECTED }

    public final String name;

    // fields: fieldName -> visibility
    public final Map<String, Vis> fields = new LinkedHashMap<>();

    // method declarations: methodName -> visibility
    public final Map<String, Vis> methodVis = new HashMap<>();

    // method implementations: methodName -> MethodInfo
    public final Map<String, MethodInfo> methods = new HashMap<>();

    // convenience
    public MethodInfo constructor = null;
    public MethodInfo destructor = null;

    public ClassDef(String name) {
        this.name = name;
    }

    public static final class MethodInfo {
        public final String className;
        public final String methodName;
        public final boolean isFunction; // procedure vs function
        public final List<String> paramNames;
        public final delphiParser.BlockContext body; // AST for the block

        public MethodInfo(String className, String methodName, boolean isFunction,
                          List<String> paramNames, delphiParser.BlockContext body) {
            this.className = className;
            this.methodName = methodName;
            this.isFunction = isFunction;
            this.paramNames = paramNames;
            this.body = body;
        }
    }
}
