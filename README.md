# Delphi Mini (ANTLR4 + Java)

This project implements a small Pascal-like language extended with Delphi-style classes:
- Classes and objects
- Constructors and destructors (explicit call)
- Encapsulation via public/private/protected sections
- Integer terminal I/O: readln(x), writeln(expr)

## Requirements
- Java 17+ (or 11+)
- ANTLR 4 (complete jar)

## Build Steps

### 1) Generate lexer/parser + visitor
From project root:

**Mac/Linux**
```bash
java -jar antlr-4.13.1-complete.jar -Dlanguage=Java -visitor -no-listener delphi.g4 -o gen
