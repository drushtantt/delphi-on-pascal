package interpreter;

import java.util.HashMap;
import java.util.Map;

public final class Frame {
    public final Map<String, Value> vars = new HashMap<>();
}
