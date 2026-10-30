#!/usr/bin/env python3
"""Derive searchable administrative-region bounding boxes from an admdongkor GeoJSON."""

import argparse
import csv
import hashlib
import json
import math
import sys
from pathlib import Path


def points(coordinates):
    if isinstance(coordinates[0], (int, float)):
        yield coordinates
    else:
        for child in coordinates:
            yield from points(child)


def merge(regions, region_id, level, name, parent, bounds):
    metadata = (region_id, level, name, parent)
    if region_id in regions:
        previous = regions[region_id]
        if previous[:4] != metadata:
            raise ValueError(f"Conflicting region metadata: {region_id}")
        bounds = [min(previous[4], bounds[0]), min(previous[5], bounds[1]),
                  max(previous[6], bounds[2]), max(previous[7], bounds[3])]
    regions[region_id] = metadata + tuple(bounds)


def feature_bounds(geometry):
    if geometry["type"] not in ("Polygon", "MultiPolygon"):
        raise ValueError("Only polygon boundaries are supported")
    coordinates = list(points(geometry["coordinates"]))
    if not coordinates or any(not math.isfinite(value) for point in coordinates for value in point[:2]):
        raise ValueError("Boundary must contain finite coordinates")
    bounds = [min(p[1] for p in coordinates), min(p[0] for p in coordinates),
              max(p[1] for p in coordinates), max(p[0] for p in coordinates)]
    if not (-90 <= bounds[0] <= bounds[2] <= 90 and -180 <= bounds[1] <= bounds[3] <= 180):
        raise ValueError("Boundary must use WGS84 coordinates")
    return bounds


def add_feature(regions, feature):
    properties = feature["properties"]
    bounds = feature_bounds(feature["geometry"])
    province = properties["sidonm"]
    district = properties["sggnm"]
    prefix = f"{province} {district} "
    if not properties["adm_nm"].startswith(prefix):
        raise ValueError(f"Unexpected administrative name: {properties['adm_nm']}")
    town = properties["adm_nm"][len(prefix):]
    merge(regions, "SIDO:" + properties["sido"], "PROVINCE", province, "", bounds)

    # Sejong has one province-level result, rather than a duplicate 'Sejong city' result.
    city_end = district.rfind("시", 1, -2) if district.endswith("구") and "\n" not in district else -1
    if properties["sido"] == "36":
        town_parent = province
    elif city_end >= 0:
        city, district_name = district[:city_end + 1], district[city_end + 1:]
        city_parent = f"{province} {city}"
        merge(regions, f"CITY:{properties['sido']}:{city}", "CITY_COUNTY_DISTRICT", city, province, bounds)
        merge(regions, "SGG:" + properties["sgg"], "CITY_COUNTY_DISTRICT", district_name, city_parent, bounds)
        town_parent = f"{city_parent} {district_name}"
    else:
        merge(regions, "SGG:" + properties["sgg"], "CITY_COUNTY_DISTRICT", district, province, bounds)
        town_parent = f"{province} {district}"
    merge(regions, "EMD:" + properties["adm_cd2"], "ADMINISTRATIVE_DISTRICT", town, town_parent, bounds)


def generate(source, output):
    source_bytes = source.read_bytes()
    collection = json.loads(source_bytes)
    regions = {}
    for feature in collection["features"]:
        add_feature(regions, feature)

    writer = csv.writer(output, delimiter="\t", lineterminator="\n")
    writer.writerow(["regionId", "level", "name", "parentName", "southwestLatitude", "southwestLongitude", "northeastLatitude", "northeastLongitude"])
    # Float repr preserves original double precision; do not round bounds inwards.
    writer.writerows(regions[key] for key in sorted(regions))
    print(f"{len(collection['features'])} features -> {len(regions)} regions", file=sys.stderr)
    print(f"source SHA-256: {hashlib.sha256(source_bytes).hexdigest()}", file=sys.stderr)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    arguments = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", newline="")
    generate(arguments.source, sys.stdout)
