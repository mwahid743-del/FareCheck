import csv, io, os, struct, sys, urllib.request, zipfile

URL = "https://raw.githubusercontent.com/dwyl/uk-postcodes-latitude-longitude-complete-csv/master/ukpostcodes.csv.zip"
OUT = "app/src/main/assets/postcodes.bin"
LAT_MIN, LAT_MAX = 51.25, 51.75
LON_MIN, LON_MAX = -0.65, 0.45

if os.path.exists(OUT) and os.path.getsize(OUT) > 100000:
    print("postcodes.bin already present")
    sys.exit(0)

print("downloading postcode data")
req = urllib.request.Request(URL, headers={"User-Agent": "farecheck-build"})
raw = urllib.request.urlopen(req, timeout=180).read()
z = zipfile.ZipFile(io.BytesIO(raw))
name = [n for n in z.namelist() if n.endswith("ukpostcodes.csv") and "__MACOSX" not in n][0]

rows = {}
with z.open(name) as f:
    for r in csv.DictReader(io.TextIOWrapper(f, encoding="utf-8")):
        try:
            lat = float(r["latitude"])
            lon = float(r["longitude"])
        except (ValueError, KeyError):
            continue
        if not (LAT_MIN <= lat <= LAT_MAX and LON_MIN <= lon <= LON_MAX):
            continue
        key = r["postcode"].replace(" ", "").upper()
        if len(key) < 5 or len(key) > 7:
            continue
        rows[key.ljust(7)] = (int(round(lat * 1e6)), int(round(lon * 1e6)))

if len(rows) < 50000:
    print("too few postcodes found:", len(rows))
    sys.exit(1)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as out:
    for key in sorted(rows):
        lat, lon = rows[key]
        out.write(key.encode("ascii") + struct.pack(">ii", lat, lon))
print("wrote", len(rows), "postcodes,", os.path.getsize(OUT), "bytes")
