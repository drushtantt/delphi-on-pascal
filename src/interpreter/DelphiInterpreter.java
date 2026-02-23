package interpreter;

import java.util.*;
import java.util.stream.Collectors;

public class DelphiInterpreter extends delphiBaseVisitor<Value> {

    private final RuntimeEnv env = new RuntimeEnv();
    private final Scanner scanner = new Scanner(System.in);

    // When parsing class members, visibility section affects subsequent members
    private ClassDef.Vis currentVis = ClassDef.Vis.PUBLIC;

    @Override
    public Value visitProgram(delphiParser.ProgramContext ctx) {
        if (ctx.typeSection() != null) visit(ctx.typeSection());
        if (ctx.varSection() != null) visit(ctx.varSection());
        if (ctx.methodImplSection() != null) visit(ctx.methodImplSection());
        return visit(ctx.block());
    }

    /* ===================== TYPE / CLASS ===================== */

    @Override
    public Value visitTypeSection(delphiParser.TypeSectionContext ctx) {
        for (delphiParser.TypeDeclContext td : ctx.typeDecl()) {
            String className = td.ID().getText();
            ClassDef cd = new ClassDef(className);

            currentVis = ClassDef.Vis.PUBLIC;

            delphiParser.ClassMembersContext members = td.classType().classMembers();
            for (var child : members.children) {
                if (child instanceof delphiParser.VisibilitySectionContext vs) {
                    currentVis = parseVis(vs.getStart().getText());
                    for (delphiParser.ClassMemberDeclContext cm : vs.classMemberDecl()) {
                        registerClassMember(cd, cm, currentVis);
                    }
                } else if (child instanceof delphiParser.ClassMemberDeclContext cm) {
                    registerClassMember(cd, cm, currentVis);
                }
            }

            env.classes.put(className, cd);
        }
        return Value.nil();
    }

    private ClassDef.Vis parseVis(String s) {
        String v = s.toLowerCase();
        return switch (v) {
            case "private" -> ClassDef.Vis.PRIVATE;
            case "protected" -> ClassDef.Vis.PROTECTED;
            default -> ClassDef.Vis.PUBLIC;
        };
    }

    private void registerClassMember(ClassDef cd, delphiParser.ClassMemberDeclContext cm, ClassDef.Vis vis) {
        if (cm.fieldDecl() != null) {
            List<String> names = cm.fieldDecl().idList().ID().stream().map(t -> t.getText()).toList();
            for (String f : names) cd.fields.put(f, vis);
            return;
        }

        // method header: declare visibility; impl comes later in methodImplSection
        var mh = cm.methodHeader();
        boolean isCtor = mh.constructorHeader() != null;
        boolean isDtor = mh.destructorHeader() != null;
        boolean isFunc = mh.functionHeader() != null;

        String methodName =
                isCtor ? mh.constructorHeader().ID().getText() :
                isDtor ? mh.destructorHeader().ID().getText() :
                (mh.procedureHeader() != null) ? mh.procedureHeader().ID().getText() :
                mh.functionHeader().ID().getText();

        cd.methodVis.put(methodName, vis);

        // placeholder (body attached after we parse methodImplSection)
        cd.methods.putIfAbsent(methodName, new ClassDef.MethodInfo(cd.name, methodName, isFunc, List.of(), null));
    }

    /* ===================== METHOD IMPLEMENTATIONS ===================== */

    @Override
    public Value visitMethodImplSection(delphiParser.MethodImplSectionContext ctx) {
        for (delphiParser.MethodImplContext mi : ctx.methodImpl()) {
            attachMethodImpl(mi);
        }
        return Value.nil();
    }

    private void attachMethodImpl(delphiParser.MethodImplContext mi) {
        var h = mi.methodImplHeader();

        String className = h.ID(0).getText();
        String methodName = h.ID(1).getText();

        ClassDef cd = env.classes.get(className);
        if (cd == null) throw new RuntimeException("Method implementation for unknown class: " + className);

        boolean isFunction = h.FUNCTION() != null;

        List<String> paramNames = new ArrayList<>();
        if (h.formalParams() != null) {
            for (var fp : h.formalParams().formalParam()) {
                for (var idTok : fp.idList().ID()) {
                    paramNames.add(idTok.getText());
                }
            }
        }

        ClassDef.MethodInfo info = new ClassDef.MethodInfo(className, methodName, isFunction, paramNames, mi.block());
        cd.methods.put(methodName, info);

        // Heuristic: constructor named Create, destructor named Destroy (common Delphi)
        if (h.CONSTRUCTOR() != null) cd.constructor = info;
        if (h.DESTRUCTOR() != null) cd.destructor = info;
    }

    /* ===================== VARS ===================== */

    @Override
    public Value visitVarSection(delphiParser.VarSectionContext ctx) {
        for (delphiParser.VarDeclContext vd : ctx.varDecl()) {
            List<String> names = vd.idList().ID().stream().map(t -> t.getText()).toList();
            String type = vd.typeName().getText().toLowerCase();

            for (String n : names) {
                if (type.equals("integer")) env.declareVar(n, Value.ofInt(0));
                else env.declareVar(n, Value.nil()); // object refs start as nil
            }
        }
        return Value.nil();
    }

    /* ===================== BLOCK / STATEMENTS ===================== */

    @Override
    public Value visitBlock(delphiParser.BlockContext ctx) {
        if (ctx.stmtList() != null) visit(ctx.stmtList());
        return Value.nil();
    }

    @Override
    public Value visitStmtList(delphiParser.StmtListContext ctx) {
        for (delphiParser.StatementContext s : ctx.statement()) {
            visit(s);
            // (Optional) early return: not used in this subset
            if (env.frame().hasReturn) break;
        }
        return Value.nil();
    }

    @Override
    public Value visitCompoundStmt(delphiParser.CompoundStmtContext ctx) {
        if (ctx.stmtList() != null) visit(ctx.stmtList());
        return Value.nil();
    }

    @Override
    public Value visitAssignment(delphiParser.AssignmentContext ctx) {
        String left = ctx.lvalue().getText();
        Value rhs = visit(ctx.expr());

        // Function return convention: inside function, "FuncName := expr" sets return value
        Frame f = env.frame();
        if (f.currentFunctionName != null && left.equalsIgnoreCase(f.currentFunctionName)) {
            f.returnValue = rhs;
            f.hasReturn = true;
            return Value.nil();
        }

        if (left.contains(".")) {
            String[] parts = left.split("\\.");
            String baseName = parts[0];
            String field = parts[1];

            Value base = env.getVar(baseName);
            if (base.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + baseName);

            ObjectInstance obj = base.objVal;
            enforceFieldVisibility(obj.klass, field);

            if (!obj.fields.containsKey(field))
                throw new RuntimeException("Unknown field: " + obj.klass.name + "." + field);

            obj.fields.put(field, rhs);
            return Value.nil();
        }

        env.setVar(left, rhs);
        return Value.nil();
    }

    /* ===================== CALLS ===================== */

    @Override
    public Value visitBuiltinOrProcCall(delphiParser.BuiltinOrProcCallContext ctx) {
        String name = ctx.ID().getText().toLowerCase();
        List<Value> args = evalActualParams(ctx.actualParams());

        if (name.equals("writeln")) {
            if (args.isEmpty()) { System.out.println(); return Value.nil(); }
            for (Value v : args) System.out.println(v.kind == Value.Kind.INT ? v.asInt() : v.toString());
            return Value.nil();
        }

        if (name.equals("readln")) {
            if (ctx.actualParams() == null || ctx.actualParams().expr().size() != 1)
                throw new RuntimeException("readln expects exactly 1 argument");

            // must be simple variable (integer)
            var e = ctx.actualParams().expr(0);
            if (!(e instanceof delphiParser.LvalExprContext lv))
                throw new RuntimeException("readln argument must be a variable");

            String varName = lv.lvalue().getText();
            if (varName.contains(".")) throw new RuntimeException("readln only supports simple integer vars");

            int x = scanner.nextInt();
            env.setVar(varName, Value.ofInt(x));
            return Value.nil();
        }

        throw new RuntimeException("Unknown procedure: " + name);
    }

    @Override
    public Value visitMethodOrStaticCall(delphiParser.MethodOrStaticCallContext ctx) {
        String left = ctx.ID(0).getText();   // could be className or varName
        String member = ctx.ID(1).getText(); // method name
        List<Value> args = evalActualParams(ctx.actualParams());

        // Static call: ClassName.Method(...)
        if (env.classes.containsKey(left)) {
            ClassDef cd = env.classes.get(left);

            // Only constructor static call is required: TClass.Create(...)
            if (cd.constructor == null) {
                // if they used a different ctor name, still allow "TClass.<CtorName>"
                var mi = cd.methods.get(member);
                if (mi != null && isConstructorImpl(mi, cd)) {
                    return executeConstructor(cd, mi, args);
                }
                throw new RuntimeException("No constructor implemented for class: " + cd.name);
            }

            if (!member.equalsIgnoreCase(cd.constructor.methodName)) {
                // allow calling the actual declared ctor method name
                var mi = cd.methods.get(member);
                if (mi != null && isConstructorImpl(mi, cd)) return executeConstructor(cd, mi, args);
                throw new RuntimeException("Unsupported static call: " + cd.name + "." + member + " (expected constructor)");
            }

            return executeConstructor(cd, cd.constructor, args);
        }

        // Instance call: obj.Method(...)
        Value recv = env.getVar(left);
        if (recv.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + left);

        ObjectInstance obj = recv.objVal;
        ClassDef cd = obj.klass;

        enforceMethodVisibility(cd, member);

        ClassDef.MethodInfo mi = cd.methods.get(member);
        if (mi == null || mi.body == null) throw new RuntimeException("Method not implemented: " + cd.name + "." + member);

        if (member.equalsIgnoreCase(cd.destructor != null ? cd.destructor.methodName : "Destroy")) {
            executeDestructor(cd, obj, mi, args);
            return Value.nil();
        }

        return executeInstanceMethod(cd, obj, mi, args);
    }

    private boolean isConstructorImpl(ClassDef.MethodInfo mi, ClassDef cd) {
        // minimal: treat first attached CONSTRUCTOR as constructor; we already set cd.constructor for CONSTRUCTOR impl.
        return cd.constructor != null && mi.methodName.equals(cd.constructor.methodName);
    }

    /* ===================== EXECUTION ENGINE ===================== */

    private Value executeConstructor(ClassDef cd, ClassDef.MethodInfo ctor, List<Value> args) {
        ObjectInstance obj = new ObjectInstance(cd);

        // Run ctor body with self + params
        Frame f = new Frame();
        bindParams(f, ctor.paramNames, args);

        env.pushFrame(f);
        env.classCtx.push(cd);
        env.selfCtx.push(obj);

        try {
            visit(ctor.body);
        } finally {
            env.selfCtx.pop();
            env.classCtx.pop();
            env.popFrame();
        }

        return Value.ofObj(obj);
    }

    private void executeDestructor(ClassDef cd, ObjectInstance obj, ClassDef.MethodInfo dtor, List<Value> args) {
        Frame f = new Frame();
        bindParams(f, dtor.paramNames, args);

        env.pushFrame(f);
        env.classCtx.push(cd);
        env.selfCtx.push(obj);

        try {
            visit(dtor.body);
        } finally {
            env.selfCtx.pop();
            env.classCtx.pop();
            env.popFrame();
        }
    }

    private Value executeInstanceMethod(ClassDef cd, ObjectInstance obj, ClassDef.MethodInfo mi, List<Value> args) {
        Frame f = new Frame();
        bindParams(f, mi.paramNames, args);

        if (mi.isFunction) {
            f.currentFunctionName = mi.methodName; // allow "MethodName := expr" return convention
            f.returnValue = Value.ofInt(0);        // default return
            f.hasReturn = false;
        }

        env.pushFrame(f);
        env.classCtx.push(cd);
        env.selfCtx.push(obj);

        try {
            visit(mi.body);
            if (mi.isFunction) return env.frame().returnValue;
            return Value.nil();
        } finally {
            env.selfCtx.pop();
            env.classCtx.pop();
            env.popFrame();
        }
    }

    private void bindParams(Frame f, List<String> paramNames, List<Value> args) {
        if (args.size() != paramNames.size()) {
            throw new RuntimeException("Argument count mismatch. Expected " + paramNames.size() + " got " + args.size());
        }
        for (int i = 0; i < paramNames.size(); i++) {
            f.vars.put(paramNames.get(i), args.get(i));
        }
    }

    /* ===================== EXPRESSIONS ===================== */

    @Override
    public Value visitIntLit(delphiParser.IntLitContext ctx) {
        return Value.ofInt(Integer.parseInt(ctx.INT().getText()));
    }

    @Override
    public Value visitLvalExpr(delphiParser.LvalExprContext ctx) {
        String lv = ctx.lvalue().getText();

        if (lv.contains(".")) {
            String[] parts = lv.split("\\.");
            String baseName = parts[0];
            String field = parts[1];

            // support "self.field" by allowing baseName == "self"
            ObjectInstance obj;
            if (baseName.equalsIgnoreCase("self")) {
                obj = env.currentSelf();
            } else {
                Value base = env.getVar(baseName);
                if (base.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + baseName);
                obj = base.objVal;
            }

            enforceFieldVisibility(obj.klass, field);

            if (!obj.fields.containsKey(field))
                throw new RuntimeException("Unknown field: " + obj.klass.name + "." + field);

            return obj.fields.get(field);
        }

        return env.getVar(lv);
    }

    @Override
    public Value visitAddSub(delphiParser.AddSubContext ctx) {
        int a = visit(ctx.expr(0)).asInt();
        int b = visit(ctx.expr(1)).asInt();
        return Value.ofInt(ctx.op.getText().equals("+") ? a + b : a - b);
    }

    @Override
    public Value visitMulDiv(delphiParser.MulDivContext ctx) {
        int a = visit(ctx.expr(0)).asInt();
        int b = visit(ctx.expr(1)).asInt();
        if (ctx.op.getText().equals("/")) {
            if (b == 0) throw new RuntimeException("Division by zero");
            return Value.ofInt(a / b);
        }
        return Value.ofInt(a * b);
    }

    @Override
    public Value visitParens(delphiParser.ParensContext ctx) {
        return visit(ctx.expr());
    }

    /* ===================== HELPERS ===================== */

    private List<Value> evalActualParams(delphiParser.ActualParamsContext ap) {
        if (ap == null) return List.of();
        return ap.expr().stream().map(this::visit).collect(Collectors.toList());
    }

    private void enforceFieldVisibility(ClassDef cd, String field) {
        ClassDef.Vis vis = cd.fields.getOrDefault(field, ClassDef.Vis.PUBLIC);

        // Private/protected allowed only within class context in this non-inheritance subset
        if ((vis == ClassDef.Vis.PRIVATE || vis == ClassDef.Vis.PROTECTED) && !env.inClassContext(cd)) {
            throw new RuntimeException("Field access denied (" + vis + "): " + cd.name + "." + field);
        }
    }

    private void enforceMethodVisibility(ClassDef cd, String method) {
        ClassDef.Vis vis = cd.methodVis.getOrDefault(method, ClassDef.Vis.PUBLIC);

        if ((vis == ClassDef.Vis.PRIVATE || vis == ClassDef.Vis.PROTECTED) && !env.inClassContext(cd)) {
            throw new RuntimeException("Method access denied (" + vis + "): " + cd.name + "." + method);
        }
    }
}
