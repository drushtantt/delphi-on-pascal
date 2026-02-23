package interpreter;

import java.util.HashMap;
import java.util.Map;

public final class Frame {
    // Variables declared/visible in this scope
    public final Map<String, Value> vars = new HashMap<>();

    // If executing inside a function, we track its name so "FuncName := expr" becomes return value
    public String currentFunctionName = null;

    // Current function return value (if any)
    public Value returnValue = Value.nil();

    public boolean hasReturn = false;
}
