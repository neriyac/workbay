"""Count what run/saves/New World's six-stage chain is holding, read off the region files.

    ./gradlew runClient, then in game: /save-all flush
    python tools/chain-count.py "run/saves/New World"

Conservation is the assertion: the TOTAL must not move, whatever the stages are doing. Reading
it any other way means opening six containers -- two of them inside bays, one inside a room --
which is twelve GUI interactions per reading and a screenshot to squint at. This is one command
and the same numbers every time.

The positions are the scratch rig's, and derived rather than found: the two chests are where the
Connectors point (workbay_registry.dat), and a bay's machine is BayGeometry.machinePos, which for
column 0 is (8, 10 + 8 * bay, 8).

Two things the NBT reader has to get right, both of which read as corrupt data rather than as a
bug: a compound's entry is `name = s(); o[name] = payload(t)`, because Python evaluates the value
before the subscript and `o[s()] = payload(t)` reads the two off the stream backwards; and TAG_Byte
is signed, so an unsigned read walks the stream out of step the first time a value goes over 127.
"""
import gzip, zlib, struct, io, sys, os

def reader(buf):
    f = io.BytesIO(buf)
    def u1(): return f.read(1)[0]
    def i4(): return struct.unpack('>i', f.read(4))[0]
    def s():
        n = struct.unpack('>H', f.read(2))[0]
        return f.read(n).decode('utf-8', 'replace')
    def payload(t):
        if t == 1: return struct.unpack('>b', f.read(1))[0]
        if t == 2: return struct.unpack('>h', f.read(2))[0]
        if t == 3: return i4()
        if t == 4: return struct.unpack('>q', f.read(8))[0]
        if t == 5: return struct.unpack('>f', f.read(4))[0]
        if t == 6: return struct.unpack('>d', f.read(8))[0]
        if t == 7:
            n = i4(); return list(f.read(n))
        if t == 8: return s()
        if t == 9:
            et = u1(); n = i4(); return [payload(et) for _ in range(n)]
        if t == 10:
            o = {}
            while True:
                tt = u1()
                if tt == 0: return o
                nm = s(); o[nm] = payload(tt)   # name BEFORE payload: `o[s()] = payload()` reads them backwards
        if t == 11:
            n = i4(); return [i4() for _ in range(n)]
        if t == 12:
            n = i4(); return [struct.unpack('>q', f.read(8))[0] for _ in range(n)]
        raise ValueError(t)
    u1(); s()
    return payload(10)

def chunk(region_dir, cx, cz):
    rx, rz = cx >> 5, cz >> 5
    path = os.path.join(region_dir, "r.%d.%d.mca" % (rx, rz))
    if not os.path.exists(path):
        return None
    with open(path, 'rb') as fh:
        raw = fh.read()
    i = ((cx & 31) + (cz & 31) * 32) * 4
    off = int.from_bytes(raw[i:i + 3], 'big') * 4096
    if off == 0:
        return None
    length = int.from_bytes(raw[off:off + 4], 'big')
    comp = raw[off + 4]
    body = raw[off + 5:off + 4 + length]
    body = zlib.decompress(body) if comp == 2 else gzip.decompress(body)
    return reader(body)

def container(region_dir, x, y, z):
    c = chunk(region_dir, x >> 4, z >> 4)
    if c is None:
        return None
    for be in c.get('block_entities', []):
        if be.get('x') == x and be.get('y') == y and be.get('z') == z:
            return be
    return None

save = sys.argv[1]
OW = os.path.join(save, 'region')
BS = os.path.join(save, 'dimensions', 'workbay', 'backshop', 'region')
spots = [
    ("1 source chest   ow 303,200,298", OW, 303, 200, 298),
    ("2 bay 0 chest    bs 8,10,8",      BS, 8, 10, 8),
    ("3 bay 1 barrel   bs 8,18,8",      BS, 8, 18, 8),
    ("4 room barrel    bs -1048570,1,7", BS, -1048570, 1, 7),
    ("5 bay 2 barrel   bs 8,26,8",      BS, 8, 26, 8),
    ("6 target chest   ow 303,200,301", OW, 303, 200, 301),
]
total = 0
for label, d, x, y, z in spots:
    be = container(d, x, y, z)
    if be is None:
        print("%-34s MISSING" % label)
        continue
    n = sum(it.get('count', 0) for it in be.get('Items', []))
    kinds = sorted({it.get('id', '?') for it in be.get('Items', [])})
    total += n
    print("%-34s %5d  %s" % (label, n, ','.join(k.split(':')[-1] for k in kinds)))
print("%-34s %5d" % ("TOTAL", total))
