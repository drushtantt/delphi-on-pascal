package interpreter;

public final class Value {
    public enum Kind { INT, OBJ, NIL }

    public final Kind kind;
    private final int intVal;
    public final ObjectInstance objVal;

    private Value(Kind k, int i, ObjectInstance o) {
        this.kind = k;
        this.intVal = i;
        this.objVal = o;
    }

    public static Value ofInt(int x) { return new Value(Kind.INT, x, null); }
    public static Value ofObj(ObjectInstance o) { return new Value(Kind.OBJ, 0, o); }
    public static Value nil() { return new Value(Kind.NIL, 0, null); }

    public int asInt() {
        if (kind != Kind.INT) throw new RuntimeException("Expected integer, got " + kind);
        return intVal;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case INT -> Integer.toString(intVal);
            case OBJ -> "<object " + objVal.klass.name + ">";
            case NIL -> "nil";
        };
    }
}
