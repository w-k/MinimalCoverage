package eu.wawr.minimalCoverage;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;

public class TopologyHelper {

    private static final double tolerance = 1/10000000000.0;

    public static Boolean IsCovered(Geometry object, Geometry mask){
        if(object.coveredBy(mask)){
            return true;
        }
        Geometry difference = Reduce(Reduce(object).difference(Reduce(mask)));
        if(difference.getArea() < tolerance){
            return true;
        }
        return false;
    }

    public static Geometry Difference(Geometry thisGeometry, Geometry otherGeometry){
        thisGeometry = Reduce(thisGeometry);
        otherGeometry = Reduce(otherGeometry);
        Geometry result = thisGeometry.difference(otherGeometry);
        return Reduce(result);
    }

    public static Geometry SymDifference(Geometry thisGeometry, Geometry otherGeometry){
        thisGeometry = Reduce(thisGeometry);
        otherGeometry = Reduce(otherGeometry);
        Geometry result = thisGeometry.symDifference(otherGeometry);
        return Reduce(result);
    }
    public static Geometry Intersection(Geometry thisGeometry, Geometry otherGeometry){
        thisGeometry = Reduce(thisGeometry);
        otherGeometry = Reduce(otherGeometry);
        Geometry result = thisGeometry.intersection(otherGeometry);
        return Reduce(result);
    }

    public static Geometry Union(Geometry thisGeometry, Geometry otherGeometry){
        thisGeometry = Reduce(thisGeometry);
        otherGeometry = Reduce(otherGeometry);
        Geometry result = thisGeometry.union(otherGeometry);
        return Reduce(result);
    }

    public static Geometry Reduce(Geometry geometry){
        return GeometryPrecisionReducer.reduce(geometry, new PrecisionModel(100000000000.0));
    }

    public static boolean Validate(Geometry geometry){
        String type = geometry.getGeometryType();
        return (type == "Polygon" || type == "MultiPolygon") && geometry.isValid();
    }

    public static boolean Equal(Geometry thisGeometry, Geometry otherGeometry){
        if(thisGeometry.equals(otherGeometry)){
            return true;
        }
        return thisGeometry.symDifference(otherGeometry).getArea() < tolerance;
    }
}
