package eu.wawr.minimalCoverage;

import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.*;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;


public class Main {

    public static void main(String[] args) throws IOException, SchemaException{
        if(args.length != 4){
            System.out.println("Use:");
            System.out.println("java -jar MinimalCoverage.jar <elements_shapefile> <range_shapefile> <id_attribute> <output_path>");
            System.out.println("Example:");
            System.out.println("java -jar MinimalCoverage.jar elements.shp range.shp id output.shp");
            return;
        }
        String elementsFile = args[0];
        String rangeFile = args[1];
        String uniqueIdentifierAttributeName = args[2];
        String outputPath = args[3];
        FeatureSource elementsSource = readShapefile(elementsFile);
        FeatureSource rangeSource = readShapefile(rangeFile);
        FeatureIterator iterator =  rangeSource.getFeatures().features();
        try{
            SimpleFeature rangeFeature = (SimpleFeature)iterator.next();
            Geometry rangeGeometry = (Geometry)rangeFeature.getDefaultGeometry();
            OptimalCoverageGenerator optimalCoverageGenerator = new OptimalCoverageGenerator(
                    elementsSource, uniqueIdentifierAttributeName);
            Set<String> result = optimalCoverageGenerator.Generate(rangeGeometry);
            FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
            Set<FeatureId> featureIds = new HashSet<>();
            for(String id : result){
                featureIds.add(filterFactory.featureId(id));
            }
            Filter filter = filterFactory.id(featureIds);
            FeatureCollection features = elementsSource.getFeatures(filter);
            writeShapefile(features, outputPath);
        } finally {
            iterator.close();
        }
    }

    private static FeatureSource readShapefile(String filename){
        try {
            File elementsFile = new File(filename);
            FileDataStore elementsStore = FileDataStoreFinder.getDataStore(elementsFile);
            return elementsStore.getFeatureSource();
        }
        catch(Exception e) {
            return null;
        }
    }

    public static void writeShapefile(FeatureCollection collection, String filename) throws IOException, SchemaException{
        File newFile = new File(filename);
        if(!newFile.createNewFile()){
            newFile.delete();
            newFile.createNewFile();
        }
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put("url", newFile.toURI().toURL());
        parameters.put("create spatial filter", Boolean.TRUE);
        ShapefileDataStore shapeFileDataStore = (ShapefileDataStore)dataStoreFactory.createNewDataStore(parameters);
        SimpleFeatureType type = DataUtilities.createType("POLYGON", "the_geom:Polygon,id:String");
        type = DataUtilities.createSubType(type, null, DefaultGeographicCRS.WGS84);
        shapeFileDataStore.createSchema(type);
        FeatureSource featureSource = shapeFileDataStore.getFeatureSource();
        FeatureStore featureStore = (FeatureStore)featureSource;
        featureStore.addFeatures(collection);
    }
}

