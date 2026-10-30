import csv
import json
import tempfile
import unittest
from pathlib import Path

from generate_region_snapshot import generate


class RegionSnapshotTest(unittest.TestCase):
    def test_bounds_keep_every_polygon_point_and_aggregate_whole_city(self):
        features = [
            self.feature("시흥시동구", "41111", "4111151000", "첫동",
                         [[[[126.12345678901235, 37.2], [126.5, 37.4], [126.12345678901235, 37.2]]]]),
            self.feature("시흥시서구", "41113", "4111351000", "둘째동",
                         [[[[126.8, 37.7], [127.2, 38.1], [126.8, 37.7]]],
                          [[[125.9, 36.9], [126.0, 37.0], [125.9, 36.9]]]])]
        regions = self.snapshot(features)
        city = regions["CITY:41:시흥시"]
        self.assertEqual(city["name"], "시흥시")
        self.assertEqual(regions["SGG:41111"]["parentName"], "경기도 시흥시")
        self.assertEqual(regions["SGG:41111"]["name"], "동구")
        self.assertEqual(float(city["southwestLatitude"]), 36.9)
        self.assertEqual(float(city["southwestLongitude"]), 125.9)
        self.assertEqual(float(city["northeastLatitude"]), 38.1)
        self.assertEqual(float(city["northeastLongitude"]), 127.2)
        self.assertEqual(float(regions["EMD:4111151000"]["southwestLongitude"]), 126.12345678901235)

    def test_sejong_has_one_parent_result(self):
        feature = self.feature("세종시", "36110", "3611051000", "첫동", [[[[127.0, 36.0], [127.5, 36.5], [127.0, 36.0]]]])
        feature["properties"].update(sido="36", sidonm="세종특별자치시", adm_nm="세종특별자치시 세종시 첫동")
        regions = self.snapshot([feature])
        self.assertEqual(len(regions), 2)
        self.assertNotIn("SGG:36110", regions)
        self.assertEqual(regions["EMD:3611051000"]["parentName"], "세종특별자치시")

    @staticmethod
    def feature(district, code, town_code, town, coordinates):
        return {"properties": {"sido": "41", "sidonm": "경기도", "sgg": code, "sggnm": district,
                               "adm_cd2": town_code, "adm_nm": f"경기도 {district} {town}"},
                "geometry": {"type": "MultiPolygon", "coordinates": coordinates}}

    @staticmethod
    def snapshot(features):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.json"
            destination = Path(directory) / "regions.tsv"
            source.write_text(json.dumps({"features": features}), encoding="utf-8")
            generate(source, destination)
            with destination.open(encoding="utf-8") as snapshot:
                return {row["regionId"]: row for row in csv.DictReader(snapshot, delimiter="\t")}


if __name__ == "__main__":
    unittest.main()
