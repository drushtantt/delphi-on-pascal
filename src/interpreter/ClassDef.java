package interpreter;

import java.util.*;

public final class ClassDef {
    public enum Vis { PUBLIC, PRIVATE, PROTECTED }

    public final String name;

    public final Map<String, Vis> fieldVis = new HashMap<>();
    public final Set<String> fields = new HashSet<>();

    public final Map<String, Vis> methodVis = new HashMap<>();
    public final Map<String, MethodInfo> methods = new HashMap<>();

    public MethodInfo constructor = null;
    public MethodInfo destructor = null;

    public ClassDef(String name) { this.name = name; }

    public static final class MethodInfo {
        public final String className;
        public final String methodName;
        public final boolean isFunction;
        public final delphiParser.MethodImplContext implCtx;
        public final List<String> paramNames;

        public MethodInfo(String cls, String m, boolean isFunc,
                          delphiParser.MethodImplContext ctx, List<String> params) {
            this.className = cls;
            this.methodName = m;
            this.isFunction = isFunc;
            this.implCtx = ctx;
            this.paramNames = params;
        }
    }
}
