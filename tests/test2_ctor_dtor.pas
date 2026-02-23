program Test2;

type
  TFoo = class
  public:
    constructor Create();
    destructor Destroy();
  end;

var
  f: TFoo;

begin
  f := TFoo.Create();
  f.Destroy();
  writeln(123);
end.
