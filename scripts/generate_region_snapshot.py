#!/usr/bin/env python3
"""Derive searchable administrative-region bounding boxes from an admdongkor GeoJSON."""

import argparse
import csv
import hashlib
import json
import math
import re
from pathlib import Path


def points(coordinates):
    if isinstance(coordinates[0], (int, float)):
        yield coordinates
    else:
        for child in coordinates:
            yield from points(child)


def generate(source, destination):
    source_bytes = source.read_bytes()
    collection = json.loads(source_bytes)
    regions = {}

    def merge(region_id, level, name, parent, bounds):
        metadata = (region_id, level, name, parent)
        if region_id in regions:
            previous = regions[region_id]
            if previous[:4] != metadata:
                raise ValueError(f"Conflicting region metadata: {region_id}")
            bounds = [min(previous[4], bounds[0]), min(previous[5], bounds[1]),
                      max(previous[6], bounds[2]), max(previous[7], bounds[3])]
        regions[region_id] = metadata + tuple(bounds)

    for feature in collection["features"]:
        properties = feature["properties"]
        if feature["geometry"]["type"] not in ("Polygon", "MultiPolygon"):
            raise ValueError("Only polygon boundaries are supported")
        coordinates = list(points(feature["geometry"]["coordinates"]))
        if not coordinates or any(not math.isfinite(value) for point in coordinates for value in point[:2]):
            raise ValueError("Boundary must contain finite coordinates")
        bounds = [min(p[1] for p in coordinates), min(p[0] for p in coordinates),
                  max(p[1] for p in coordinates), max(p[0] for p in coordinates)]
        if not (-90 <= bounds[0] <= bounds[2] <= 90 and -180 <= bounds[1] <= bounds[3] <= 180):
            raise ValueError("Boundary must use WGS84 coordinates")
        province = properties["sidonm"]
        district = properties["sggnm"]
        prefix = f"{province} {district} "
        if not properties["adm_nm"].startswith(prefix):
            raise ValueError(f"Unexpected administrative name: {properties['adm_nm']}")
        town = properties["adm_nm"][len(prefix):]
        merge("SIDO:" + properties["sido"], "PROVINCE", province, "", bounds)

        # Sejong has one province-level result, rather than a duplicate 'Sejong city' result.
        if properties["sido"] == "36":
            town_parent = province
        else:
            general_district = re.fullmatch(r"(.+시)(.+구)", district)
            if general_district:
                city, district_name = general_district.groups()
                city_parent = f"{province} {city}"
                merge(f"CITY:{properties['sido']}:{city}", "CITY_COUNTY_DISTRICT", city, province, bounds)
                merge("SGG:" + properties["sgg"], "CITY_COUNTY_DISTRICT", district_name, city_parent, bounds)
                town_parent = f"{city_parent} {district_name}"
            else:
                merge("SGG:" + properties["sgg"], "CITY_COUNTY_DISTRICT", district, province, bounds)
                town_parent = f"{province} {district}"
        merge("EMD:" + properties["adm_cd2"], "ADMINISTRATIVE_DISTRICT", town, town_parent, bounds)

    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t", lineterminator="\n")
        writer.writerow(["regionId", "level", "name", "parentName", "southwestLatitude", "southwestLongitude", "northeastLatitude", "northeastLongitude"])
        # Float repr preserves original double precision; do not round bounds inwards.
        writer.writerows(regions[key] for key in sorted(regions))
    print(f"{len(collection['features'])} features -> {len(regions)} regions")
    print(f"source SHA-256: {hashlib.sha256(source_bytes).hexdigest()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()
    generate(arguments.source, arguments.destination)
