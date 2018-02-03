package com.waterpc.test.geotoolscoorditransf.business;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Geometry;

public class ShapeCoordinateTransfBusiness {

	final String strWKTMercator = "PROJCS[\"World_Mercator\"," + "GEOGCS[\"GCS_WGS_1984\"," + "DATUM[\"WGS_1984\","
			+ "SPHEROID[\"WGS_1984\",6378137,298.257223563]]," + "PRIMEM[\"Greenwich\",0],"
			+ "UNIT[\"Degree\",0.017453292519943295]]," + "PROJECTION[\"Mercator_1SP\"],"
			+ "PARAMETER[\"False_Easting\",0]," + "PARAMETER[\"False_Northing\",0],"
			+ "PARAMETER[\"Central_Meridian\",0]," + "PARAMETER[\"latitude_of_origin\",0]," + "UNIT[\"Meter\",1]]";

	public Geometry lonlat2WebMactor(Geometry geom) {
		try {
			// 这里是以OGC WKT形式定义的是World Mercator投影，网页地图一般使用该投影

			// CoordinateReferenceSystem crsTarget =
			// CRS.parseWKT(strWKTMercator);
			CoordinateReferenceSystem crsTarget = CRS.decode("EPSG:3857");

			// 投影转换
			MathTransform transform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, crsTarget);
			return JTS.transform(geom, transform);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param inputShp 源shape文件的路径
	 * @param outputShp 目标shape文件的路径
	 */
	public void projectShape(String inputShp, String outputShp) {
		try {
			// 源shape文件
			ShapefileDataStore oldShapeDS = (ShapefileDataStore) new ShapefileDataStoreFactory()
					.createDataStore(new File(inputShp).toURI().toURL());
			// 创建目标shape文件对象
			Map<String, Serializable> params = new HashMap<String, Serializable>();
			FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			params.put(ShapefileDataStoreFactory.URLP.key, new File(outputShp).toURI().toURL());
			ShapefileDataStore ds = (ShapefileDataStore) factory.createNewDataStore(params);
			// 设置属性
			SimpleFeatureSource fs = oldShapeDS.getFeatureSource(oldShapeDS.getTypeNames()[0]);
			//可以从原始的shape文件中取出该shape的空间坐标系以作为坐标转换函数的一个参数，其实这才是正确的
			//CoordinateReferenceSystem sourceCrs=fs.getSchema().getCoordinateReferenceSystem();
			// 下面这行还有其他写法，根据源shape文件的simpleFeatureType可以不用retype，而直接用fs.getSchema设置
			CoordinateReferenceSystem desCrs = CRS.parseWKT(strWKTMercator);
			ds.createSchema(SimpleFeatureTypeBuilder.retype(fs.getSchema(), desCrs));

			// 设置writer
			FeatureWriter<SimpleFeatureType, SimpleFeature> writer = ds.getFeatureWriter(ds.getTypeNames()[0],
					Transaction.AUTO_COMMIT);

			// 写记录
			SimpleFeatureIterator it = fs.getFeatures().features();
			try {
				while (it.hasNext()) {
					SimpleFeature f = it.next();
					SimpleFeature fNew = writer.next();
					fNew.setAttributes(f.getAttributes());
					//几何属性就是the_geom
					Geometry geom = lonlat2WebMactor((Geometry) f.getAttribute("the_geom"));
					fNew.setAttribute("the_geom", geom);
				}
			} finally {
				//必须要释放SimpleFeatureIterator资源
				it.close();
			}
			writer.write();
			writer.close();
			ds.dispose();
			oldShapeDS.dispose();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
