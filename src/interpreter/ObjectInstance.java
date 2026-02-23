package interpreter;

import java.util.HashMap;
import java.util.Map;

public final class ObjectInstance {
    public final ClassDef klass;
    public final Map<String, Value> fields = new HashMap<>();

    public ObjectInstance(ClassDef k) {
        this.klass = k;
        // initialize declared fields
        for (String f : k.fields.keySet()) {
            // this language subset uses only integers for fields in tests; default to 0
            fields.put(f, Value.ofInt(0));
        }
    }
}
