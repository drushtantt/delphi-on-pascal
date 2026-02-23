program Test2;

type
  TFoo = class
  public:
    constructor Create();
    destructor Destroy();
  end;

constructor TFoo.Create();
begin
  writeln(1);
end;

destructor TFoo.Destroy();
begin
  writeln(2);
end;

var f: TFoo;

begin
  f := TFoo.Create();
  f.Destroy();
end.
