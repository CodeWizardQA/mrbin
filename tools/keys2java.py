import re, sys
src = open(sys.argv[1]).read()
out = {}
for cls in ("FakeKeyset", "PkgDerivedKey3Keyset"):
    body = src[src.index("class " + cls):]
    body = body[:body.index("};\n\nclass") if "};\n\nclass" in body else len(body)]
    fields = {}
    for m in re.finditer(r'(\w+)\[\]\s*=\s*\{([^}]*)\}', body):
        name, vals = m.group(1), m.group(2)
        bs = [int(v, 0) for v in re.findall(r'0x[0-9A-Fa-f]+|\b\d+\b', vals)]
        if name not in fields:
            fields[name] = bytes(bs).hex().upper()
    out[cls] = fields
for cls, f in out.items():
    for k in ("Modulus","PublicExponent","PrivateExponent","Prime1","Prime2","Exponent1","Exponent2","Coefficient"):
        print(cls, k, len(f[k])//2, f[k])
