package eu.wawr.minimalCoverage;


import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import java.io.IOException;
import java.util.*;

public class OptimalCoverageGenerator {

    private FeatureSource featureSource;
    private SimpleFeatureType TYPE;
    private FilterFactory2 filterFactory;
    private final double tolerance = 1/10000000000.0;
    private String idAttribute;

    public OptimalCoverageGenerator(FeatureSource featureSource, String idAttribute) throws SchemaException{
        this.featureSource = featureSource;
        this.idAttribute = idAttribute;
        TYPE = DataUtilities.createType("POLYGON", "the_geom:Polygon,id:String");
//        TYPE = DataUtilities.createSubType(TYPE, null, DefaultGeographicCRS.WGS84);
        filterFactory = CommonFactoryFinder.getFilterFactory2();
    }

    public Set<String> Generate(Geometry query) throws IOException, SchemaException{
        FeatureType schema = featureSource.getSchema();
        String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();
        Filter filter = filterFactory.intersects(
                filterFactory.property(geometryPropertyName),
                filterFactory.literal(query));

        SimpleFeatureCollection inRangeFeatures = (SimpleFeatureCollection)featureSource.getFeatures(filter);
        List<SimpleFeature> clippedFeatures = Clip(inRangeFeatures, query);
        List<SimpleFeature> choppedFeatures = Chop(clippedFeatures);
        Set<String> notFullyCoveredIds = GetNotFullyCoveredIds(clippedFeatures);
        IntersectionsMap intersectionMap = new IntersectionsMap(choppedFeatures, clippedFeatures);
        IntersectionsMap invertedIntersections = intersectionMap.Invert();
        return GetMinimumSetCover(
                intersectionMap.Map().keySet(),
                invertedIntersections.Map(),
                notFullyCoveredIds);
    }

    private List<SimpleFeature> Clip(SimpleFeatureCollection features, Geometry range) throws SchemaException{
        List<SimpleFeature> clippedFeatures = new ArrayList<>();
        SimpleFeatureIterator iterator = features.features();
        try{
            while(iterator.hasNext()){
                SimpleFeature feature = iterator.next();
                Geometry geometry = GetGeometry(feature);
                if(geometry.intersects(range)){
                    Geometry clippedGeometry = TopologyHelper.Intersection(geometry, range);
                    if(TopologyHelper.Validate(clippedGeometry)){
                        SimpleFeature clippedFeature = MakeFeature(clippedGeometry, feature.getIdentifier().getID());
                        clippedFeatures.add(clippedFeature);
                    }
                }
            }
            return clippedFeatures;
        } finally {
            iterator.close();
        }
    }

    private List<SimpleFeature> Chop(List<SimpleFeature> features) throws SchemaException{
        List<SimpleFeature> result = new ArrayList<>(features);
        for(SimpleFeature feature : features){
            result = Chop(result, feature);
        }
        return RemoveDuplicates(result);
    }

    private List<SimpleFeature> Chop(List<SimpleFeature> features, SimpleFeature mask) throws SchemaException {
        List<SimpleFeature> result = new ArrayList<>();
        for(SimpleFeature feature : features){
            result.addAll(Chop(feature, mask));
        }
        return result;
    }

    private List<SimpleFeature> Chop(SimpleFeature object, SimpleFeature mask) throws SchemaException{
        List<SimpleFeature> choppedFeatures = new ArrayList<>();
        Geometry objectGeometry = (Geometry) object.getDefaultGeometry();
        Geometry maskGeometry = (Geometry) mask.getDefaultGeometry();
        if(!TopologyHelper.Validate(objectGeometry)) {
            return choppedFeatures;
        } else if(!TopologyHelper.Validate(maskGeometry)){
            choppedFeatures.add(object);
        } else if(objectGeometry.disjoint(maskGeometry) || objectGeometry.touches(maskGeometry)){
            choppedFeatures.add(object);
        } else if(TopologyHelper.Equal(objectGeometry, maskGeometry)){
            choppedFeatures.add(object);
        } else if(objectGeometry.contains(maskGeometry)){
            Geometry difference = (Geometry)objectGeometry.clone();
            for(int i=0;i<maskGeometry.getNumGeometries(); i++){
                Geometry geometry = TopologyHelper.Reduce(maskGeometry.getGeometryN(i));
                if(geometry.getGeometryType() == "Polygon"){
                    difference = TopologyHelper.Difference(difference, geometry);
                }
            }
            for(int i=0;i<difference.getNumGeometries(); i++){
                Geometry geometry = difference.getGeometryN(i);
                if(TopologyHelper.Validate(geometry)){
                    choppedFeatures.add(MakeFeature(geometry));
                }
            }
            choppedFeatures.add(mask);
        } else if(maskGeometry.contains(objectGeometry)){
            choppedFeatures.add(object);
        } else if (objectGeometry.intersects(maskGeometry)){
            Geometry intersection = TopologyHelper.Intersection(objectGeometry, maskGeometry);
            Geometry left = TopologyHelper.Difference(objectGeometry, maskGeometry);
            for(int i=0;i<left.getNumGeometries(); i++){
                Geometry geometry = left.getGeometryN(i);
                if(geometry.getGeometryType() == "Polygon"){
                    choppedFeatures.add(MakeFeature(geometry));
                }
            }
            for(int i=0;i<intersection.getNumGeometries(); i++){
                Geometry geometry = intersection.getGeometryN(i);
                if(geometry.getGeometryType() == "Polygon"){
                    choppedFeatures.add(MakeFeature(geometry));
                }
            }
        }
        return choppedFeatures;
    }

    private List<SimpleFeature> RemoveDuplicates(List<SimpleFeature> features){
        List<SimpleFeature> uniqueFeatures = new ArrayList<>();
        for(int i=0; i<features.size(); i++){
            Geometry firstGeometry = GetGeometry(features.get(i));
            boolean isUnique = true;
            for(SimpleFeature feature : uniqueFeatures){
                Geometry g = GetGeometry(feature);
                if(TopologyHelper.Equal(GetGeometry(feature), firstGeometry)){
                    isUnique = false;
                    break;
                }
            }
            if(isUnique){
                uniqueFeatures.add(features.get(i));
            }
        }
        return uniqueFeatures;
    }

    private Geometry GetGeometry(SimpleFeature feature){
        Geometry geometry = (Geometry)feature.getDefaultGeometry();
        return TopologyHelper.Reduce(geometry);
    }


    private SimpleFeature MakeFeature (Geometry geometry) throws SchemaException {
        return SimpleFeatureBuilder.build(
                TYPE,
                new Object[]{geometry, UUID.randomUUID().toString()},
                null);
    }

    private SimpleFeature MakeFeature (Geometry geometry, String id) throws SchemaException {
        return SimpleFeatureBuilder.build(
                TYPE,
                new Object[]{geometry, id},
                null);
    }

    private String GetId(Feature feature){
        return feature.getProperty(idAttribute).getValue().toString();
    }

    private Set<String> GetNotFullyCoveredIds(List<SimpleFeature> clippedFeatures){
        Set<String> result = new HashSet<>();
        for(SimpleFeature feature : clippedFeatures){
            Geometry thisGeometry = (Geometry)feature.getDefaultGeometry();
            String id = feature.getProperty(idAttribute).getValue().toString();
            FeatureCollection otherFeatureCollection = FilterById(id, clippedFeatures, true);
            Geometry otherGeometry = GetUnionGeometry(otherFeatureCollection);
            thisGeometry = TopologyHelper.Reduce(thisGeometry.union());
            if(!TopologyHelper.Validate(thisGeometry) || !TopologyHelper.Validate(otherGeometry)){
                continue;
            }
            if(!TopologyHelper.IsCovered(thisGeometry, otherGeometry)){
                result.add(id);
            }
        }
        return result;
    }

    private FeatureCollection FilterById(String id, List<SimpleFeature> featureCollection, Boolean except){
        List<String> ids = new ArrayList<>();
        ids.add(id);
        return FilterByIds(ids, featureCollection, except);
    }

    private FeatureCollection FilterByIds(List<String> ids, List<SimpleFeature> featureCollection, Boolean except){
        DefaultFeatureCollection outputFeatures = new DefaultFeatureCollection();
        SimpleFeatureType type = SimpleFeatureTypeBuilder.copy(((SimpleFeature)featureCollection.get(0)).getType());
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for(Feature f : featureCollection){
            SimpleFeature feature = (SimpleFeature)f;
            boolean contained = ids.contains(GetId(feature));
            boolean use = except ? !contained : contained;
            if(use){
                builder.add(feature.getDefaultGeometry());
                outputFeatures.add(builder.buildFeature(GetId(feature)));
            }
        }
        return outputFeatures;
    }

    private Geometry GetUnionGeometry(FeatureCollection featureCollection){
        FeatureIterator iterator = featureCollection.features();
        try{
            Geometry unionGeometry = null;
            while(iterator.hasNext()){
                SimpleFeature feature = (SimpleFeature)iterator.next();
                Geometry geometry = (Geometry)feature.getDefaultGeometry();
                if(unionGeometry == null){
                    unionGeometry = TopologyHelper.Reduce(geometry);
                } else {
                    unionGeometry = TopologyHelper.Union(unionGeometry, geometry);
                }
            }
            return unionGeometry;
        } finally {
            iterator.close();
        }
    }


    private Set<String> GetMinimumSetCover(Set<String> universe, Map<String, Set<String>> subsets,
                                                 Set<String> selectedSubsetKeys){
        Set<String> selectedSubsetsUnion = new HashSet<>();
        for(String key : selectedSubsetKeys){
            if(subsets.containsKey(key)){
                selectedSubsetsUnion.addAll(subsets.get(key));
            }
        }
        while(!selectedSubsetsUnion.equals(universe)){
            String largestSet = PopLargestSet(subsets, selectedSubsetsUnion);
            if(largestSet == null){
                break;
            }
            selectedSubsetKeys.add(largestSet);
        }
        return selectedSubsetKeys;
    }

    private String PopLargestSet(Map<String, Set<String>> sets, Set<String> excluded){
        int largestCount = Integer.MIN_VALUE;
        String largestKey = null;
        for(String key : sets.keySet()){
            Set<String> value = sets.get(key);
            int count = GetCountExcluding(value, excluded);
            if(count > largestCount && count > 0){
                largestCount = count;
                largestKey = key;
            }
        }
        if(largestKey == null){
            return null;
        } else{
            excluded.addAll(sets.remove(largestKey));
            return largestKey;
        }
    }

    private int GetCountExcluding(Set<String> counted, Set<String> excluded){
        int counter = 0;
        for(String value : counted){
            if(!excluded.contains(value)){
                counter++;
            }
        }
        return counter;
    }
}
