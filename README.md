# Delphi Full (ANTLR4 + Java)

Implements a small Pascal-like language with Delphi-style OO:
- Classes and objects
- Constructors & destructors (explicit call like obj.Destroy())
- Encapsulation: public/private/protected sections enforced at runtime
- Integer I/O: readln(x), writeln(expr)
- Full AST-walking execution of method bodies (constructor/procedure/function/destructor)

## Requirements
- Java 11+ (17 recommended)
- ANTLR 4 complete jar

## Build

### 1) Generate parser/lexer + visitor
From project root:

Mac/Linux:
```bash
java -jar antlr-4.13.1-complete.jar -Dlanguage=Java -visitor -no-listener delphi.g4 -o gen
