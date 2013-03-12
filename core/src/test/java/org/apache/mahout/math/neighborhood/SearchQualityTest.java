package org.apache.mahout.math.neighborhood;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.common.distance.EuclideanDistanceMeasure;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.random.WeightedThing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(value = Parameterized.class)
public class SearchQualityTest {
  private static final int NUM_DATA_POINTS = 1 << 13;
  private static final int NUM_QUERIES = 1 << 8;
  private static final int NUM_DIMENSIONS = 40;
  private static final int NUM_RESULTS = 2;

  private final Searcher searcher;
  private final Matrix dataPoints;
  private final Matrix queries;
  private Pair<List<List<WeightedThing<Vector>>>, Long> reference;

  @Parameterized.Parameters
  public static List<Object[]> generateData() {
    RandomUtils.useTestSeed();
    Matrix dataPoints = LumpyData.lumpyRandomData(NUM_DATA_POINTS, NUM_DIMENSIONS);
    Matrix queries = LumpyData.lumpyRandomData(NUM_QUERIES, NUM_DIMENSIONS);

    DistanceMeasure distanceMeasure = new EuclideanDistanceMeasure();

    Searcher bruteSearcher = new BruteSearch(distanceMeasure);
    bruteSearcher.addAll(dataPoints);
    Pair<List<List<WeightedThing<Vector>>>, Long> reference = getResultsAndRuntime(bruteSearcher, queries);

    double bruteSearchAvgTime = reference.getSecond() / (queries.numRows() * 1.0);
    System.out.printf("BruteSearch: avg_time(1 query) %f[s]\n", bruteSearchAvgTime);

    return Arrays.asList(new Object[][]{
        // NUM_PROJECTIONS = 3
        // SEARCH_SIZE = 10
        {new ProjectionSearch(distanceMeasure, 3, 10), dataPoints, queries, reference},
        {new FastProjectionSearch(distanceMeasure, 3, 10), dataPoints, queries, reference},
        {new LocalitySensitiveHashSearch(distanceMeasure, 10), dataPoints, queries, reference,},
        // NUM_PROJECTIONS = 5
        // SEARCH_SIZE = 5
        {new ProjectionSearch(distanceMeasure, 5, 5), dataPoints, queries, reference},
        {new FastProjectionSearch(distanceMeasure, 5, 5), dataPoints, queries, reference},
        {new LocalitySensitiveHashSearch(distanceMeasure, 5), dataPoints, queries, reference},
    }
    );
  }

  public SearchQualityTest(Searcher searcher, Matrix dataPoints, Matrix queries,
                           Pair<List<List<WeightedThing<Vector>>>, Long> reference) {
    this.searcher = searcher;
    this.dataPoints = dataPoints;
    this.queries = queries;
    this.reference = reference;
  }

  @Test
  public void testOverlapAndRuntime() {
    assertThat("Search not empty initially", searcher.size(), equalTo(0));

    searcher.addAll(dataPoints);
    Pair<List<List<WeightedThing<Vector>>>, Long> results = getResultsAndRuntime(searcher, queries);

    int numFirstMatches = 0;
    int numMatches = 0;
    StripWeight stripWeight = new StripWeight();
    for (int i = 0; i < queries.numRows(); ++i) {
      List<WeightedThing<Vector>> referenceVectors = reference.getFirst().get(i);
      List<WeightedThing<Vector>> resultVectors = results.getFirst().get(i);
      if (referenceVectors.get(0).getValue().equals(resultVectors.get(0).getValue())) {
        ++numFirstMatches;
      }
      for (Vector v : Iterables.transform(referenceVectors, stripWeight)) {
        for (Vector w : Iterables.transform(resultVectors, stripWeight)) {
          if (v.equals(w)) {
            ++numMatches;
          }
        }
      }
    }

    double bruteSearchAvgTime = reference.getSecond() / (queries.numRows() * 1.0);
    double searcherAvgTime = results.getSecond() / (queries.numRows() * 1.0);
    System.out.printf("%s: first matches %d [%d]; total matches %d [%d]; avg_time(1 query) %f(s) [%f]\n",
        searcher.getClass().getName(), numFirstMatches, queries.numRows(),
        numMatches, queries.numRows() * NUM_RESULTS, searcherAvgTime, bruteSearchAvgTime);

    assertThat("Closest vector returned doesn't match", numFirstMatches, is(queries.numRows()));
    assertThat("Searcher " + searcher.getClass().getName() + " slower than brute",
        bruteSearchAvgTime, greaterThan(searcherAvgTime));
  }

  public static Pair<List<List<WeightedThing<Vector>>>, Long> getResultsAndRuntime(Searcher searcher,
                                                                                   Iterable<? extends Vector> queries) {
    long start = System.currentTimeMillis();
    List<List<WeightedThing<Vector>>> results = searcher.search(queries, NUM_RESULTS);
    long end = System.currentTimeMillis();
    return new Pair<List<List<WeightedThing<Vector>>>, Long>(results, end - start);
  }

  static class StripWeight implements Function<WeightedThing<Vector>, Vector> {
    @Override
    public Vector apply(WeightedThing<Vector> input) {
      Preconditions.checkArgument(input != null);
      return input.getValue();
    }
  }
}
