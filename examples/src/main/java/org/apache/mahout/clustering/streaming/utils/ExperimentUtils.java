package org.apache.mahout.clustering.streaming.utils;

import com.google.common.collect.Maps;
import org.apache.hadoop.io.Text;
import org.apache.mahout.clustering.streaming.cluster.BallKMeans;
import org.apache.mahout.clustering.streaming.cluster.StreamingKMeans;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.distance.CosineDistanceMeasure;
import org.apache.mahout.math.Centroid;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.math.neighborhood.BruteSearch;
import org.apache.mahout.math.neighborhood.FastProjectionSearch;

import java.util.List;
import java.util.Map;

public class ExperimentUtils {
  /**
   * Helper method to get the base of a document from the path. So, if the path is /alt.atheism/1234 the path returned
   * is alt.atheism.
   * @param name raw file path.
   * @return the path base from the name.
   */
  public static String getNameBase(String name) {
    int lastSlash = name.lastIndexOf('/');
    int postNextLastSlash = name.lastIndexOf('/', lastSlash - 1) + 1;
    return name.substring(postNextLastSlash, lastSlash);
  }

  /**
   * Computes the actual clusters as given by the keys of the documents.
   * @param inputPaths the keys/paths of the documents.
   * @param reducedVectors the projected vectors.
   * @return a map of keys (each corresponding to a cluster) to Centroids.
   */
  public static Map<String, Centroid> computeActualClusters(List<String> inputPaths,
                                                            List<Centroid> reducedVectors) {
    Map<String, Centroid> actualClusters = Maps.newHashMap();
    for (int i = 0; i < reducedVectors.size(); ++i) {
      String inputPath = getNameBase(inputPaths.get(i));
      Centroid actualCluster = actualClusters.get(inputPath);
      if (actualCluster == null) {
        actualCluster = reducedVectors.get(i).clone();
        actualClusters.put(inputPaths.get(i), actualCluster);
      } else {
        actualCluster.update(reducedVectors.get(i));
      }
    }
    return actualClusters;
  }

  /**
   * Computes the actual clusters as given by the keys of the documents.
   * @param dirIterable an iterable through the sequence file (SequenceFileDirIterable).
   * @return a map of keys (each corresponding to a cluster) to Centroids.
   */
  public static Map<String, Centroid> computeActualClusters(Iterable<Pair<Text, VectorWritable>> dirIterable) {
    Map<String, Centroid> actualClusters = Maps.newHashMap();
    int clusterId = 0;
    for (Pair<Text, VectorWritable> pair : dirIterable) {
      String clusterName = pair.getFirst().toString();
      Centroid centroid = actualClusters.get(clusterName);
      if (centroid == null) {
        centroid = new Centroid(++clusterId, pair.getSecond().get().clone(), 1);
        actualClusters.put(clusterName, centroid);
        continue;
      }
      centroid.update(pair.getSecond().get());
    }
    return actualClusters;
  }

  public static Iterable<Centroid> clusterBallKMeans(List<Centroid> datapoints, int numClusters) {
    BallKMeans clusterer = new BallKMeans(new BruteSearch(new CosineDistanceMeasure()), numClusters, 20);
    clusterer.cluster(datapoints, false);
    return clusterer;
  }

  public static Iterable<Centroid> clusterStreamingKMeans(List<Centroid> datapoints, int numClusters) {
    StreamingKMeans clusterer = new StreamingKMeans(new FastProjectionSearch(new CosineDistanceMeasure(), 3, 2),
        numClusters, 1e-6);
    clusterer.cluster(datapoints);
    return clusterer;
  }

  public static Iterable<Centroid> clusterOneByOneStreamingKMeans(List<Centroid> datapoints, int numClusters) {
    StreamingKMeans clusterer = new StreamingKMeans(new FastProjectionSearch(new CosineDistanceMeasure(), 3, 2),
        numClusters, 1e-6);
    for (Centroid datapoint : datapoints) {
      clusterer.cluster(datapoint);
    }
    return clusterer;
  }

}
