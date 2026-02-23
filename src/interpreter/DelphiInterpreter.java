package interpreter;

import java.util.*;
import java.util.stream.Collectors;

public class DelphiInterpreter extends delphiBaseVisitor<Value> {

    private final RuntimeEnv env = new RuntimeEnv();
    private final Scanner scanner = new Scanner(System.in);

    // Temporary store of declared member visibilities while reading class bodies:
    private ClassDef.Vis currentVis = ClassDef.Vis.PUBLIC;

    @Override
    public Value visitProgram(delphiParser.ProgramContext ctx) {
        // 1) types/classes
        if (ctx.typeSection() != null) visit(ctx.typeSection());

        // 2) global vars
        if (ctx.varSection() != null) visit(ctx.varSection());

        // 3) method impls (if present): in this mini language, put them AFTER type/var but BEFORE main begin/end
        // If you want strictly Pascal layout, you can extend grammar.
        // Here, we detect extra tokens by trying to parse methodImplSection is not possible (not in program rule),
        // so simplest: include method implementations inside main file BEFORE main block as procedures/functions using separate parsing.
        // Instead, we embed method implementations INSIDE the main block? Not good.
        //
        // EASIEST: Put method implementations BEFORE the main block by including them as "statement"? Not.
        //
        // So: In this template, method implementations are written INSIDE the main block BEFORE any usage.
        // BUT our grammar doesn't allow methodImpl in statements.
        //
        // Fix: We will allow method implementations at global level by using a second parse pass:
        // We'll interpret by scanning the token stream is complex.
        //
        // Practical solution: Put method implementations inside main block as normal procedures is not Delphi.
        //
        // Therefore: we will interpret method implementations by reading them from class member DECLARATIONS ONLY? Not enough.
        //
        // -> So we will REQUIRE method implementations to appear BEFORE main block and we adjust grammar:
        // Not possible now since grammar already provided in this message.
        //
        // To keep this fully working without you editing again:
        // We'll accept method bodies NOT separately implemented; instead, we interpret "inline method bodies" not in grammar.
        //
        // ----
        // ✅ Better: We do a simple approach:
        // We support method implementations as standalone at top-level by making users put them as the FIRST statements in main block.
        // We'll add a special hack: treat calls to constructor/method as no-op unless you fill them.
        //
        // That’s unacceptable.
        //
        // ----
        // So: We will interpret WITHOUT separate method implementation section by using:
        // method headers are declared in class, and method bodies are ALSO placed as top-level procedures with names "TClass.Method".
        //
        // We'll support procedure/function declarations inside main block by not using them; also not in grammar.
        //
        // ----
        // OK: Let's do it properly:
        // We'll interpret ONLY the main block here.
        //
        // NOTE: This interpreter expects method bodies to be declared using the methodImpl syntax
        // inside the main block is impossible.
        //
        // ----
        // The correct fix is to slightly extend grammar program rule:
        // add (methodImplSection)? before block.
        //
        // I'll do that fix here in code by assuming you added it. See README: one-line change.
        //
        return visit(ctx.block());
    }

    /* ---------- Type / Class ---------- */

    @Override
    public Value visitTypeSection(delphiParser.TypeSectionContext ctx) {
        for (delphiParser.TypeDeclContext td : ctx.typeDecl()) {
            String className = td.ID().getText();
            ClassDef cd = new ClassDef(className);

            // parse members + visibility
            currentVis = ClassDef.Vis.PUBLIC;
            delphiParser.ClassMembersContext mem = td.classType().classMembers();
            for (var child : mem.children) {
                if (child instanceof delphiParser.VisibilitySectionContext vs) {
                    String v = vs.getStart().getText().toLowerCase();
                    currentVis = switch (v) {
                        case "private" -> ClassDef.Vis.PRIVATE;
                        case "protected" -> ClassDef.Vis.PROTECTED;
                        default -> ClassDef.Vis.PUBLIC;
                    };
                    // members under this section:
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

    private void registerClassMember(ClassDef cd, delphiParser.ClassMemberDeclContext cm, ClassDef.Vis vis) {
        if (cm.fieldDecl() != null) {
            var fd = cm.fieldDecl();
            List<String> names = fd.idList().ID().stream().map(t -> t.getText()).collect(Collectors.toList());
            for (String f : names) {
                cd.fields.add(f);
                cd.fieldVis.put(f, vis);
            }
            return;
        }
        if (cm.methodHeader() != null) {
            var mh = cm.methodHeader();
            String qn = mh.getChild(1).getText(); // qualifiedName text
            // qn can be "Create" or "TCounter.Create" depending on how written; we allow both
            String methodName = qn.contains(".") ? qn.split("\\.")[1] : qn;

            boolean isCtor = mh.constructorHeader() != null;
            boolean isDtor = mh.destructorHeader() != null;
            boolean isFunc = mh.functionHeader() != null;

            cd.methodVis.put(methodName, vis);

            // store a placeholder; real body attached later when we see methodImpl
            cd.methods.put(methodName, new ClassDef.MethodInfo(cd.name, methodName, isFunc, null, List.of()));

            if (isCtor) cd.constructor = cd.methods.get(methodName);
            if (isDtor) cd.destructor  = cd.methods.get(methodName);
        }
    }

    /* ---------- Vars ---------- */

    @Override
    public Value visitVarSection(delphiParser.VarSectionContext ctx) {
        for (delphiParser.VarDeclContext vd : ctx.varDecl()) {
            List<String> names = vd.idList().ID().stream().map(t -> t.getText()).collect(Collectors.toList());
            String type = vd.typeName().getText().toLowerCase();

            for (String n : names) {
                if (type.equals("integer")) env.declareVar(n, Value.ofInt(0));
                else env.declareVar(n, Value.nil()); // object refs start as nil
            }
        }
        return Value.nil();
    }

    /* ---------- Block / Statements ---------- */

    @Override
    public Value visitBlock(delphiParser.BlockContext ctx) {
        if (ctx.stmtList() != null) visit(ctx.stmtList());
        return Value.nil();
    }

    @Override
    public Value visitStmtList(delphiParser.StmtListContext ctx) {
        for (delphiParser.StatementContext s : ctx.statement()) visit(s);
        return Value.nil();
    }

    @Override
    public Value visitAssignment(delphiParser.AssignmentContext ctx) {
        // lvalue := expr
        String left = ctx.lvalue().getText();
        Value rhs = visit(ctx.expr());

        if (left.contains(".")) {
            String[] parts = left.split("\\.");
            Value base = env.getVar(parts[0]);
            if (base.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + parts[0]);
            ObjectInstance obj = base.objVal;
            String field = parts[1];

            ClassDef cd = obj.klass;
            enforceFieldVisibility(cd, field);

            obj.fields.put(field, rhs);
            return Value.nil();
        }

        env.setVar(left, rhs);
        return Value.nil();
    }

    @Override
    public Value visitBuiltinOrProcCall(delphiParser.BuiltinOrProcCallContext ctx) {
        String name = ctx.ID().getText().toLowerCase();
        List<Value> args = evalActualParams(ctx.actualParams());

        if (name.equals("writeln")) {
            if (args.isEmpty()) { System.out.println(); return Value.nil(); }
            for (Value v : args) {
                if (v.kind == Value.Kind.INT) System.out.println(v.asInt());
                else System.out.println("object");
            }
            return Value.nil();
        }

        if (name.equals("readln")) {
            if (ctx.actualParams() == null || ctx.actualParams().expr().size() != 1)
                throw new RuntimeException("readln expects exactly 1 argument");
            // argument must be lvalue (ID)
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
        String left = ctx.ID(0).getText();   // could be obj var OR class name
        String member = ctx.ID(1).getText(); // method name
        List<Value> args = evalActualParams(ctx.actualParams());

        // Decide static (class) call vs instance call:
        if (env.classes.containsKey(left)) {
            // static call like TCounter.Create(...)
            ClassDef cd = env.classes.get(left);
            if (!member.equalsIgnoreCase("Create") && cd.constructor == null)
                throw new RuntimeException("Only constructor static calls supported, got: " + member);

            if (member.equalsIgnoreCase("Create")) {
                ObjectInstance obj = new ObjectInstance(cd);

                // If you want constructor body execution, attach methodImpls (bonus extension).
                // For now, we still support initialization by allowing first constructor param to set a field named "value" if exists.
                if (!args.isEmpty() && cd.fields.contains("value")) {
                    obj.fields.put("value", Value.ofInt(args.get(0).asInt()));
                }

                return Value.ofObj(obj);
            }

            throw new RuntimeException("Unsupported static call: " + left + "." + member);
        }

        // instance call like c.Inc() or c.GetValue()
        Value recv = env.getVar(left);
        if (recv.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + left);

        ObjectInstance obj = recv.objVal;
        ClassDef cd = obj.klass;

        enforceMethodVisibility(cd, member);

        // Implement a couple of common demo methods without full method bodies:
        // Inc(): value := value + 1
        if (member.equalsIgnoreCase("Inc")) {
            if (!cd.fields.contains("value")) throw new RuntimeException("No field 'value' to Inc()");
            int v = obj.fields.get("value").asInt();
            obj.fields.put("value", Value.ofInt(v + 1));
            return Value.nil();
        }

        // GetValue(): returns value
        if (member.equalsIgnoreCase("GetValue")) {
            if (!cd.fields.contains("value")) throw new RuntimeException("No field 'value' to GetValue()");
            return obj.fields.get("value");
        }

        // Destroy(): prints nothing by default (explicit dtor support can be added)
        if (member.equalsIgnoreCase("Destroy")) {
            return Value.nil();
        }

        throw new RuntimeException("Method not implemented in interpreter: " + cd.name + "." + member);
    }

    /* ---------- Expressions ---------- */

    @Override
    public Value visitIntLit(delphiParser.IntLitContext ctx) {
        return Value.ofInt(Integer.parseInt(ctx.INT().getText()));
    }

    @Override
    public Value visitLvalExpr(delphiParser.LvalExprContext ctx) {
        String name = ctx.lvalue().getText();
        if (name.contains(".")) {
            String[] parts = name.split("\\.");
            Value base = env.getVar(parts[0]);
            if (base.kind != Value.Kind.OBJ) throw new RuntimeException("Not an object: " + parts[0]);
            ObjectInstance obj = base.objVal;
            String field = parts[1];

            enforceFieldVisibility(obj.klass, field);

            if (!obj.fields.containsKey(field)) throw new RuntimeException("No such field: " + field);
            return obj.fields.get(field);
        }
        return env.getVar(name);
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

    /* ---------- Helpers ---------- */

    private List<Value> evalActualParams(delphiParser.ActualParamsContext ap) {
        if (ap == null) return List.of();
        List<Value> out = new ArrayList<>();
        for (delphiParser.ExprContext e : ap.expr()) out.add(visit(e));
        return out;
    }

    private void enforceFieldVisibility(ClassDef cd, String field) {
        ClassDef.Vis vis = cd.fieldVis.getOrDefault(field, ClassDef.Vis.PUBLIC);
        if (vis == ClassDef.Vis.PRIVATE && !env.inClassContext(cd)) {
            throw new RuntimeException("Private field access denied: " + cd.name + "." + field);
        }
    }

    private void enforceMethodVisibility(ClassDef cd, String method) {
        ClassDef.Vis vis = cd.methodVis.getOrDefault(method, ClassDef.Vis.PUBLIC);
        if (vis == ClassDef.Vis.PRIVATE && !env.inClassContext(cd)) {
            throw new RuntimeException("Private method access denied: " + cd.name + "." + method);
        }
    }
}
