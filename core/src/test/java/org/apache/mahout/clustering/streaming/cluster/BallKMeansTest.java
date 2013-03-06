/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.clustering.streaming.cluster;

import com.google.common.collect.Lists;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.distance.EuclideanDistanceMeasure;
import org.apache.mahout.clustering.streaming.search.BruteSearch;
import org.apache.mahout.clustering.streaming.search.Searcher;
import org.apache.mahout.clustering.streaming.search.UpdatableSearcher;
import org.apache.mahout.math.*;
import org.apache.mahout.math.function.Functions;
import org.apache.mahout.math.function.VectorFunction;
import org.apache.mahout.math.random.MultiNormal;
import org.apache.mahout.math.random.WeightedThing;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BallKMeansTest {

  private static final int NUM_DATA_POINTS = 10000;
  private static final int NUM_DIMENSIONS = 4;
  private static final int NUM_ITERATIONS = 20;

  private static Pair<List<Centroid>, List<Centroid>> syntheticData =
      DataUtils.sampleMultiNormalHypercube(NUM_DIMENSIONS, NUM_DATA_POINTS);
  private static final int K1 = 100;

  @Test
  public void testBasicClustering() {
    List<? extends WeightedVector> data = cubishTestData(1);

    BallKMeans r = new BallKMeans(new BruteSearch(new EuclideanDistanceMeasure()), 6, 20);
    r.cluster(data);
    for (Centroid centroid : r) {
      for (int i = 0; i < 10; i++) {
        System.out.printf("%10.4f", centroid.get(i));
      }
      System.out.printf("\n");
    }
  }

  @Test
  public void testClustering() {
    UpdatableSearcher searcher = new BruteSearch(new EuclideanDistanceMeasure());
    BallKMeans clusterer = new BallKMeans(searcher, 1 << NUM_DIMENSIONS, NUM_ITERATIONS);

    long startTime = System.currentTimeMillis();
    clusterer.cluster(syntheticData.getFirst());
    long endTime = System.currentTimeMillis();

    assertEquals("Total weight not preserved", totalWeight(syntheticData.getFirst()),
        totalWeight(clusterer), 1e-9);

    // Verify that each corner of the cube has a centroid very nearby.
    // This is probably FALSE for large-dimensional spaces!
    double maxWeight = 0;
    for (Vector mean : syntheticData.getSecond()) {
      WeightedThing<Vector> v = searcher.search(mean, 1).get(0);
      maxWeight = Math.max(v.getWeight(), maxWeight);
    }
    assertTrue("Maximum weight too large " + maxWeight, maxWeight < 0.05);
    double clusterTime = (endTime - startTime) / 1000.0;
    System.out.printf("%s\n%.2f for clustering\n%.1f us per row\n\n",
        searcher.getClass().getName(), clusterTime,
        clusterTime / syntheticData.getFirst().size() * 1e6);

    // verify that the total weight of the centroids near each corner is correct
    double[] cornerWeights = new double[1 << NUM_DIMENSIONS];
    Searcher trueFinder = new BruteSearch(new EuclideanDistanceMeasure());
    for (Vector trueCluster : syntheticData.getSecond()) {
      trueFinder.add(trueCluster);
    }
    for (Centroid centroid : clusterer) {
      WeightedThing<Vector> closest = trueFinder.search(centroid, 1).get(0);
      cornerWeights[((Centroid)closest.getValue()).getIndex()] += centroid.getWeight();
    }
    int expectedNumPoints = NUM_DATA_POINTS / (1 << NUM_DIMENSIONS);
    for (double v : cornerWeights) {
      System.out.printf("%f ", v);
    }
    System.out.println();
    for (double v : cornerWeights) {
      assertEquals(expectedNumPoints, v, 0);
    }
  }
  @Test
  public void testInitialization() {
    // start with super clusterable data
    List<? extends WeightedVector> data = cubishTestData(0.01);

    // just do initialization of ball k-means.  This should drop a point into each of the clusters
    BallKMeans r = new BallKMeans(new BruteSearch(new EuclideanDistanceMeasure()), 6, 20);
    r.cluster(data);

    // put the centroids into a matrix
    Matrix x = new DenseMatrix(6, 5);
    int row = 0;
    for (Centroid c : r) {
      x.viewRow(row).assign(c.viewPart(0, 5));
      row++;
    }

    // verify that each column looks right.  Should contain zeros except for a single 6.
    final Vector columnNorms = x.aggregateColumns(new VectorFunction() {
      @Override
      public double apply(Vector f) {
        // return the sum of three discrepancy measures
        return Math.abs(f.minValue()) + Math.abs(f.maxValue() - 6) + Math.abs(f.norm(1) - 6);
      }
    });
    // verify all errors are nearly zero
    assertEquals(0, columnNorms.norm(1) / columnNorms.size(), 0.1);

    // verify that the centroids are a permutation of the original ones
    SingularValueDecomposition svd = new SingularValueDecomposition(x);
    Vector s = svd.getS().viewDiagonal().assign(Functions.div(6));
    assertEquals(5, s.getLengthSquared(), 0.05);
    assertEquals(5, s.norm(1), 0.05);
  }

  private List<? extends WeightedVector> cubishTestData(double radius) {
    List<WeightedVector> data = Lists.newArrayListWithCapacity(K1 + 5000);
    int row = 0;

    MultiNormal g = new MultiNormal(radius, new ConstantVector(0, 10));
    for (int i = 0; i < K1; i++) {
      data.add(new WeightedVector(g.sample(), 1, row++));
    }

    for (int i = 0; i < 5; i++) {
      Vector m = new DenseVector(10);
      m.set(i, i == 0 ? 6 : 6);
      MultiNormal gx = new MultiNormal(radius, m);
      for (int j = 0; j < 1000; j++) {
        data.add(new WeightedVector(gx.sample(), 1, row++));
      }
    }
    return data;
  }

  private double totalWeight(Iterable<? extends Vector> data) {
    double sum = 0;
    for (Vector row : data) {
      if (row instanceof WeightedVector) {
        sum += ((WeightedVector)row).getWeight();
      } else {
        sum++;
      }
    }
    return sum;
  }
}