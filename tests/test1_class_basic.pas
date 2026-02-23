program Test1;

type
  TCounter = class
  private:
    value: integer;
  public:
    constructor Create(start: integer);
    procedure Inc();
    function GetValue(): integer;
  end;

constructor TCounter.Create(start: integer);
begin
  value := start;
end;

procedure TCounter.Inc();
begin
  value := value + 1;
end;

function TCounter.GetValue(): integer;
begin
  GetValue := value;
end;

var
  c: TCounter;
  x: integer;

begin
  c := TCounter.Create(10);
  c.Inc();
  x := c.GetValue();
  writeln(x);   { expect 11 }
end.
