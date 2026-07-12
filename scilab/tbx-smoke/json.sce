// json smoke: JSONParse only -- JSONWrite (macros/JSONWrite.sci) is an EMPTY stub
// (`function JSON = JSONWrite(Struct)` with no body and no assignment to JSON) so the
// brief's suggested toJSON/fromJSON round trip is not implemented by this toolbox; see
// the finding in the wave-2 report. This smoke instead calls the one real macro,
// JSONParse, on a single-line JSON string with a string, a number and a vector field
// (avoiding mgetl()/multi-line strcat() and nested objects/arrays-of-objects, which
// the toolbox's own README flags as separate matrix-reformatting logic) and asserts
// field-level equality against values a broken/no-op parser would not produce.
//
// Ground truth: hand-traced from macros/JSONParse.sci's own algorithm and confirmed by
// running it: strcat() is a no-op on an already-single-line string; the "],\s*["
// matrix-reformat step is skipped (no such pattern here); the delimiter substitution
// "{"->"struct(", "}"->")", ":"->"," turns the input into the Scilab expression
// struct("name", "Scilab", "value", 42, "items", [1, 2, 3]), which evstr() evaluates
// directly -- so the parsed struct's fields should come back exactly as written.
// (The literal below uses ""-doubling, not a '...' string: this Scilab build rejects
// a '-delimited string that contains a literal " as a "Heterogeneous string" parse
// error -- found the hard way while writing this smoke.)
json_str = "{""name"": ""Scilab"", ""value"": 42, ""items"": [1, 2, 3]}";
s = JSONParse(json_str);
smoke_ok = (s.name == "Scilab") & (s.value == 42) & and(s.items == [1 2 3]);
