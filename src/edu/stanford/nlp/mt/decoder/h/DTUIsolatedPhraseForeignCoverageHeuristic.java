package edu.stanford.nlp.mt.decoder.h;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.IntPair;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class DTUIsolatedPhraseForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

  private static final double MINUS_INF = -10000.0;

  public static final String DEBUG_PROPERTY = "ipfcHeuristicDebug";
  //public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "true"));
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  final IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer;
	final Scorer<FV> scorer;

  @Override
	public Object clone() throws CloneNotSupportedException {
    return super.clone();
	}
	
	/**
	 * 
	 */
	public DTUIsolatedPhraseForeignCoverageHeuristic(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
    System.err.println("Heuristic: "+getClass());
  }

  @Override
  public double getHeuristicDelta(Hypothesis<TK, FV> hyp, CoverageSet cs) {
    //return DTU ? Math.min(getHeuristicDeltaDTU(hyp),getHeuristicDeltaStandard(hyp)) : getHeuristicDeltaStandard(hyp);
    //return DTU ? getHeuristicDeltaDTU(hyp) : getHeuristicDeltaStandard(hyp);
    return getHeuristicDeltaStandard(hyp);
  }

  /**
   * For a given coverage set C of a given hypothesis, this finds all contiguous segments in the coverage set
   * (e.g., if C = {1,5,6,10}, then segments are {5-6} and {10}), and computes future cost as follows:
   * fcost(C) = fcost(1-10) - fcost(5-6) - fcost(10).
   * 
   * This does not overestimate future cost (as opposed to getHeuristicDeltaStandard), even when dealing
   * with discontinuous phrases. However, future cost estimate is often poorer than with getHeuristicDeltaStandard.
   */
  @SuppressWarnings("unused")
  private double getHeuristicDeltaDTU(Hypothesis<TK, FV> hyp) {

    double oldH = hyp.preceedingHyp.h;

    CoverageSet coverage = hyp.foreignCoverage;
		int startEdge = coverage.nextClearBit(0);
    int endEdge = hyp.foreignSequence.size()-1;
    if (endEdge < startEdge)
      return 0.0;

    //System.err.printf("================================\ndeltaDTU: for %s oldH=%f\n",coverage, oldH);

    double newH = hSpanScores.getScore(startEdge, endEdge);
    //System.err.printf("newH(%d-%d) = %f\n", startEdge, hyp.foreignSequence.size()-1,newH);

    for (Iterator<IntPair> it = coverage.getSegmentIterator(); it.hasNext(); ) {
      IntPair span = it.next();
      if (span.getSource() < startEdge) { // skip:
        assert (span.getTarget() <= startEdge);
        continue;
      }
      double localH = hSpanScores.getScore(span.getSource(), span.getTarget());
      if (!Double.isNaN(localH) && !Double.isInfinite(localH)) {
        newH -= localH;
        //System.err.printf(" newH(%d-%d) -=: %f\n", span.getSource(), span.getTarget(), localH);
      }
		}

    if((Double.isInfinite(newH) || newH == MINUS_INF) && (Double.isInfinite(oldH) || oldH == MINUS_INF))
      return 0.0;
    
    double deltaH = newH - oldH;
    //System.err.printf("deltaH = newH - oldH: %f = %f - %f\n", deltaH, newH, oldH);
    ErasureUtils.noop(deltaH);
    return deltaH;
  }

  /**
   * For a given coverage set C of a given hypothesis, this finds all the gaps in the coverage set
   * (e.g., if C = {1,5,10}, then gaps are 2-4 and 6-9), and sums the future costs associated with all
   * gaps (e.g., future-cost(2-4) + future-cost(6-9)).
   *
   * This is the same as in Moses, though this may sometimes over estimate future cost with discontinuous
   * phrases.
   */
	private double getHeuristicDeltaStandard(Hypothesis<TK, FV> hyp) {
    
    double oldH = hyp.preceedingHyp.h;
		double newH = 0.0;

    CoverageSet coverage = hyp.foreignCoverage;
		int startEdge = coverage.nextClearBit(0);

    if(Double.isNaN(oldH)) {
      System.err.printf("getHeuristicDelta:\n");
      System.err.printf("coverage: %s\n", hyp.foreignCoverage);
      System.err.println("old H: "+oldH);
      throw new RuntimeException();
    }

    int foreignSize = hyp.foreignSequence.size();
		for (int endEdge; startEdge < foreignSize; startEdge = coverage.nextClearBit(endEdge)) {
			endEdge = coverage.nextSetBit(startEdge);
			
			if (endEdge == -1) {
				endEdge = hyp.foreignSequence.size();
			}
      
      double localH = hSpanScores.getScore(startEdge, endEdge-1);

      if (Double.isNaN(localH)) {
        System.err.printf("Bad retrieved score for %d:%d ==> %f\n", startEdge, endEdge-1, localH);
        throw new RuntimeException();
      }

      newH += localH;
      if (Double.isNaN(newH)) {
        System.err.printf("Bad total retrieved score for %d:%d ==> %f (localH=%f)\n", startEdge, endEdge-1, newH, localH);
        throw new RuntimeException();
      }
		}
    if((Double.isInfinite(newH) || newH == MINUS_INF) && (Double.isInfinite(oldH) || oldH == MINUS_INF))
      return 0.0;
    double delta = newH - oldH;
    ErasureUtils.noop(delta);
    //if(Double.isInfinite(delta) || Double.isNaN(delta)) {
      //System.err.println("h delta is not valid: "+delta);
      //System.err.println("newH: "+newH);
      //System.err.println("oldH: "+oldH);
    //}
    return delta;
  }

	private SpanScores hSpanScores;
	
	@Override
	public double getInitialHeuristic(Sequence<TK> foreignSequence,
			List<List<ConcreteTranslationOption<TK>>> options, int translationId) {
		
		int foreignSequenceSize = foreignSequence.size();
		
		SpanScores viterbiSpanScores = new SpanScores(foreignSequenceSize);
		
		if (DEBUG) {
			System.err.println("IsolatedPhraseForeignCoverageHeuristic");
			System.err.printf("Foreign Sentence: %s\n", foreignSequence);
			
			System.err.println("Initial Spans from PhraseTable");
			System.err.println("------------------------------");
		}
		
		// initialize viterbiSpanScores
    System.err.println("Lists of options: "+options.size());
    assert(options.size() == 1 || options.size() == 2); // options[0]: phrases without gaps; options[1]: phrases with gaps
    System.err.println("size: "+options.size());
    for (int i=0; i<options.size(); ++i) {
      for (ConcreteTranslationOption<TK> option : options.get(i)) {
        Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence, option, translationId);
        List<FeatureValue<FV>> phraseFeatures = phraseFeaturizer.phraseListFeaturize(f);
        double score = scorer.getIncrementalScore(phraseFeatures), childScore = 0.0;
        final int terminalPos;
        if (i==0) {
          terminalPos = option.foreignPos + option.abstractOption.foreign.size()-1;
          if (score > viterbiSpanScores.getScore(option.foreignPos, terminalPos)) {
            viterbiSpanScores.setScore(option.foreignPos, terminalPos, score);
            if (Double.isNaN(score)) {
              System.err.printf("Bad Viterbi score: score[%d,%d]=%.3f\n", option.foreignPos, terminalPos, score);
              throw new RuntimeException();
            }
          }
        } else {
          terminalPos = option.foreignCoverage.length()-1;
          // Find all gaps:
          CoverageSet cs = option.foreignCoverage;
          //System.err.println("coverage set: "+cs);
          int startIdx, endIdx = 0;
          childScore = 0.0;
          while (true) {
            startIdx = cs.nextClearBit(cs.nextSetBit(endIdx));
            endIdx = cs.nextSetBit(startIdx)-1;
            if(endIdx < 0)
              break;
            childScore += viterbiSpanScores.getScore(startIdx, endIdx);
            //System.err.printf("range: %d-%d\n", startIdx, endIdx);
          }
          double totalScore = score + childScore;
          double oldScore = viterbiSpanScores.getScore(option.foreignPos, terminalPos);
          if (totalScore > oldScore) {
            viterbiSpanScores.setScore(option.foreignPos, terminalPos, totalScore);
            if (Double.isNaN(totalScore)) {
              System.err.printf("Bad Viterbi score[%d,%d]: score=%.3f childScore=%.3f\n",
                option.foreignPos, terminalPos, score, childScore);
              throw new RuntimeException();
            }
            if (DEBUG)
              System.err.printf("Improved with gaps: %.3f -> %.3f\n", oldScore, totalScore);

          }
        }
        if (DEBUG) {
          System.err.printf("\t%d:%d:%d %s->%s score: %.3f %.3f\n",
            option.foreignPos, terminalPos, i, option.abstractOption.foreign, option.abstractOption.translation, score, childScore);
          System.err.printf("\t\tFeatures: %s\n", phraseFeatures);
        }
      }

      if (DEBUG) {
        System.err.println("Initial Minimums");
        System.err.println("------------------------------");

        System.err.print("          last = ");
        for (int endPos = 0; endPos < foreignSequenceSize; endPos++)
          System.err.printf("%9d ", endPos);
        System.err.println();
        for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
          System.err.printf("\t%d-last scores: ", startPos);
          for (int endPos = 0; endPos < foreignSequenceSize; endPos++) {
            if (startPos > endPos) {
              System.err.printf("          ");
            } else {
              System.err.printf("%9.3f ", viterbiSpanScores.getScore(startPos, endPos));
            }
          }
          System.err.printf("\n");
        }
      }



      if (DEBUG) {
        System.err.println();
        System.err.println("Merging span scores");
        System.err.println("-------------------");
      }

      // Viterbi combination of spans
      for (int spanSize = 2; spanSize <= foreignSequenceSize; spanSize++) {
        if (DEBUG) {
          System.err.printf("\n* Merging span size: %d\n", spanSize);
        }
        for (int startPos = 0; startPos <= foreignSequenceSize-spanSize; startPos++) {
          int terminalPos = startPos + spanSize-1;
          double bestScore = viterbiSpanScores.getScore(startPos, terminalPos);
          for (int centerEdge = startPos+1; centerEdge <= terminalPos; centerEdge++) {
            double combinedScore = viterbiSpanScores.getScore(startPos, centerEdge-1) +
                         viterbiSpanScores.getScore(centerEdge, terminalPos);
            if (combinedScore > bestScore) {
              if (DEBUG) {
                System.err.printf("\t%d:%d updating to %.3f from %.3f\n", startPos, terminalPos, combinedScore, bestScore);
              }
              bestScore = combinedScore;
            }
          }
          viterbiSpanScores.setScore(startPos, terminalPos, bestScore);
        }
      }
    }

    if (DEBUG) {
			System.err.println();
			System.err.println("Final Scores");
			System.err.println("------------");
      System.err.print("          last = ");
      for (int endPos = 0; endPos < foreignSequenceSize; endPos++)
        System.err.printf("%9d ", endPos);
      System.err.println();
			for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
        System.err.printf("\t%d-last scores: ", startPos);
        for (int endPos = 0; endPos < foreignSequenceSize; endPos++) {
          if (startPos > endPos) {
            System.err.printf("          ");
          } else {
            System.err.printf("%9.3f ", viterbiSpanScores.getScore(startPos, endPos));
          }
        }
        System.err.printf("\n");
      }


		}
		
		hSpanScores = viterbiSpanScores;
		
		double hCompleteSequence = hSpanScores.getScore(0, foreignSequenceSize-1); 
		if (DEBUG) {
			System.err.println("Done IsolatedForeignCoverageHeuristic");
		}

    if(Double.isInfinite(hCompleteSequence) || Double.isNaN(hCompleteSequence))
      return MINUS_INF;
    return hCompleteSequence;
	}
		
	private static class SpanScores {
		final double[] spanValues;
		final int terminalPositions;
		public SpanScores(int length) {
			terminalPositions = length+1;			
			spanValues = new double[terminalPositions*terminalPositions];
			Arrays.fill(spanValues, Double.NEGATIVE_INFINITY);
		}
		
		public double getScore(int startPosition, int endPosition) {
			return spanValues[startPosition*terminalPositions + endPosition];
		}
		
		public void setScore(int startPosition, int endPosition, double score) {
			spanValues[startPosition*terminalPositions + endPosition] = score;
		}
	}
}
