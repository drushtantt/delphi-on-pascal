program Test3;

type
  TSecret = class
  private:
    value: integer;
  public:
    constructor Create(x: integer);
    function GetValue(): integer;
  end;

constructor TSecret.Create(x: integer);
begin
  value := x;
end;

function TSecret.GetValue(): integer;
begin
  GetValue := value;
end;

var
  s: TSecret;
  y: integer;

begin
  s := TSecret.Create(99);
  y := s.GetValue();
  writeln(y);     { expect 99 }

  { Should FAIL (private field from outside) }
  y := s.value;
  writeln(y);
end.
