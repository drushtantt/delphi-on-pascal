import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import interpreter.DelphiInterpreter;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <file.pas>");
            System.exit(1);
        }

        String code = Files.readString(Path.of(args[0]));
        CharStream input = CharStreams.fromString(code);

        delphiLexer lexer = new delphiLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        delphiParser parser = new delphiParser(tokens);

        ParseTree tree = parser.program();

        DelphiInterpreter interpreter = new DelphiInterpreter();
        interpreter.visit(tree);
    }
}
