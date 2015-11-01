package eu.wawr.minimalCoverage;


import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;

public class IntersectionsMap {

    private Map<String, Set<String>> map;

    public IntersectionsMap(){
        map = new HashMap<>();
    }

    public IntersectionsMap(List<SimpleFeature> keyFeatures, List<SimpleFeature> valueFeatures){
        map = new HashMap<>();
        for(SimpleFeature keyFeature : keyFeatures){
            String keyFeatureId = keyFeature.getProperty("id").getValue().toString();
            Geometry keyGeometry = (Geometry)keyFeature.getDefaultGeometry();
            for(SimpleFeature valueFeature : valueFeatures){
                String valueFeatureId = valueFeature.getProperty("id").getValue().toString();
                Geometry valueGeometry = (Geometry)valueFeature.getDefaultGeometry();
                if(TopologyHelper.IsCovered(keyGeometry, valueGeometry)){
                    Add(keyFeatureId, valueFeatureId);
                }
            }
        }
    }


    public Map<String, Set<String>> Map(){
        return map;
    }

    public void Add(String key, String value){
        if(map.containsKey(key)){
            map.get(key).add(value);
        } else{
            Set<String> valueSet = new HashSet<>();
            valueSet.add(value);
            map.put(key, valueSet);
        }
    }

    public IntersectionsMap Invert(){
        IntersectionsMap invertedIntersectionsMap = new IntersectionsMap();
        for(String key : map.keySet()){
            for(String value : map.get(key)){
                invertedIntersectionsMap.Add(value, key);
            }
        }
        return invertedIntersectionsMap;
    }
}