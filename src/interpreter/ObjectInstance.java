package interpreter;

import java.util.*;

public final class ObjectInstance {
    public final ClassDef klass;
    public final Map<String, Value> fields = new HashMap<>();

    public ObjectInstance(ClassDef k) {
        this.klass = k;
        for (String f : k.fields) {
            fields.put(f, Value.ofInt(0)); // default integer
        }
    }
}
