# MinimalCoverage

Tool for finding the optimal coverage of a given range with overlapping polygons at fixed positions.

It takes two shapefiles as input: one with the overlapping polygons and one with the polygons representing the queried range. The output is a shapefile with the polygons that represent the (approximately) optimal coverage.

Command line usage:
java -jar MinimalCoverage.jar <elements_shapefile> <range_shapefile> <id_attribute> <output_path>"

For example:
java -jar MinimalCoverage.jar elements.shp range.shp id output.shp

