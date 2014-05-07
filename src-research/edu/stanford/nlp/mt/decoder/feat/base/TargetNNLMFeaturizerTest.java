/**
 * 
 */
package edu.stanford.nlp.mt.decoder.feat.base;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import edu.stanford.nlp.mt.base.CombinedPhraseGenerator;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.InfererBuilder;
import edu.stanford.nlp.mt.decoder.InfererBuilderFactory;
import edu.stanford.nlp.mt.decoder.CubePruningNNLMDecoder.CubePruningNNLMDecoderBuilder;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerFactory;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.h.HeuristicFactory;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilterFactory;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.PhraseGeneratorFactory;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.ScorerFactory;
import edu.stanford.nlp.mt.decoder.util.UnconstrainedOutputSpace;
import edu.stanford.nlp.mt.lm.NNLMState;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * @author Thang Luong
 *
 */
public class TargetNNLMFeaturizerTest {
  public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nnlmFile = PREFIX + "test/inputs/tgt3.nplm";
  public static final String lgModel = PREFIX + "test/inputs/mt06.flt_giga.lm.gz"; // kenlm.bin"; //
  public static final String phraseTable = PREFIX + "test/inputs/dev12tune.phrase-table.gz";
  
  @Test
  public void test() throws IOException {
    TargetNNLMFeaturizer nplmFeat = new TargetNNLMFeaturizer("nnlm="+nnlmFile, "cache=0", "id=srcNPLM");
    String tgtStr = "<s> construction if so law government ,";
    Sequence<IString> tgtSent = IString.getIStringSequence(tgtStr.split("\\s+"));
    int tgtStartPos = 5; // 
    
    NNLMState state = nplmFeat.getScoreMulti(tgtStartPos, tgtSent);
    double score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    System.err.println(score);
    assertEquals(-9.937389999731584, score, 1e-5);
    
    state = nplmFeat.getScore(tgtStartPos, tgtSent);
    score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    System.err.println(score);
    assertEquals(-9.937389999731584, score, 1e-5);
  }

  private static String makePair(String label, String value) {
    return String.format("%s:%s", label, value);
  }
  
  @Test
  public void testCubePruningNNLMDecode() throws IOException, CloneNotSupportedException {
    InfererBuilder<IString, String> infererBuilder = getInferer("cube_nnlm");
    
    // load NNLM
    String nnlmType = "target";
    int cacheSize = 100000;
    int miniBatchSize = 100;
    ((CubePruningNNLMDecoderBuilder<IString, String>)  infererBuilder).loadNNLM(nnlmFile, nnlmType, cacheSize, miniBatchSize);
    
    // decoder
    Inferer<IString, String> decoder = infererBuilder.build();
    
    String src = "余下 的 事 , 就 是 必须 令 行 禁止 , 任何 公仆 若 敢 违抗 , 一律 依纪 依法 严处 。";
    OutputSpace<IString,String> outputSpace = new UnconstrainedOutputSpace<IString,String>(); // see OutputSpaceFactory
    List<Sequence<IString>> targets = null; // allow all outputs
    List<RichTranslation<IString, String>> translations = decoder.nbest
        (IString.getIStringSequence(src), 0, new InputProperties(), outputSpace, targets, 5);
    for (RichTranslation<IString, String> richTranslation : translations) { System.err.println(richTranslation); }
    assertEquals("余下 servant 违抗 依纪 严处 putting do prohibits the , any if be law . , , to would ||| LM: -1.2442E3 LinearDistortion: -75 ||| -6.3712E2 ||| 73129", translations.get(0).toString());
    assertEquals("余下 servant 违抗 依纪 严处 putting do the , forbidden any if be law . , , to would ||| LM: -1.2466E3 LinearDistortion: -73 ||| -6.379E2 ||| 73129", translations.get(1).toString());
    assertEquals("余下 servant 违抗 依纪 严处 putting do the , forbidden , any if law . be , to would ||| LM: -1.2466E3 LinearDistortion: -73 ||| -6.379E2 ||| 73129", translations.get(2).toString());
    assertEquals("余下 servant 违抗 依纪 严处 putting do the , forbidden any if be law . , , to enough ||| LM: -1.2482E3 LinearDistortion: -73 ||| -6.3871E2 ||| 73339", translations.get(3).toString());
    assertEquals("余下 servant 违抗 依纪 严处 putting do the , forbidden if be law . , , any to would ||| LM: -1.2476E3 LinearDistortion: -75 ||| -6.3878E2 ||| 73130", translations.get(4).toString());
  }
  
  @Test
  public void testCubePruningDecode() throws IOException, CloneNotSupportedException {
    InfererBuilder<IString, String> infererBuilder = getInferer("cube");
    
    // decoder
    Inferer<IString, String> decoder = infererBuilder.build();
    
    String src = "余下 的 事 , 就 是 必须 令 行 禁止 , 任何 公仆 若 敢 违抗 , 一律 依纪 依法 严处 。";
    OutputSpace<IString,String> outputSpace = new UnconstrainedOutputSpace<IString,String>(); // see OutputSpaceFactory
    List<Sequence<IString>> targets = null; // allow all outputs
    List<RichTranslation<IString, String>> translations = decoder.nbest
        (IString.getIStringSequence(src), 0, new InputProperties(), outputSpace, targets, 5);
    for (RichTranslation<IString, String> richTranslation : translations) { System.err.println(richTranslation); }
    
    assertEquals(translations.get(0).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , i would ||| LM: -1.238E3 LinearDistortion: -55 ||| -6.2999E2"), true);
    assertEquals(translations.get(1).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , i would ||| LM: -1.238E3 LinearDistortion: -55 ||| -6.2999E2"), true);
    assertEquals(translations.get(2).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , would ||| LM: -1.2382E3 LinearDistortion: -55 ||| -6.3008E2"), true);
    assertEquals(translations.get(3).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , would ||| LM: -1.2382E3 LinearDistortion: -55 ||| -6.3008E2"), true);
    assertEquals(translations.get(4).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant if , i would ||| LM: -1.2383E3 LinearDistortion: -55 ||| -6.3014E2"), true);
  }
  
  @Test
  public void testCubePruningNNLMDecodeWithPseudoNNLM() throws IOException, CloneNotSupportedException {
    /* CubePrunningNNLMDecoder */
    InfererBuilder<IString, String> infererBuilder = getInferer("cube_nnlm");
    // load pseudo NNLM
    String nnlmType = "pseudo"; int cacheSize = -1; int miniBatchSize = -1;
    ((CubePruningNNLMDecoderBuilder<IString, String>)  infererBuilder).loadNNLM(lgModel, nnlmType, cacheSize, miniBatchSize);
    Inferer<IString, String> decoder = ((CubePruningNNLMDecoderBuilder<IString, String>)  infererBuilder).build();
    
    // translate
    String src = "余下 的 事 , 就 是 必须 令 行 禁止 , 任何 公仆 若 敢 违抗 , 一律 依纪 依法 严处 。";
    OutputSpace<IString,String> outputSpace = new UnconstrainedOutputSpace<IString,String>(); // see OutputSpaceFactory
    List<Sequence<IString>> targets = null; // allow all outputs
    List<RichTranslation<IString, String>> translations = decoder.nbest
        (IString.getIStringSequence(src), 0, new InputProperties(), outputSpace, targets, 5);
    
    /* CubePrunningDecoder */
    InfererBuilder<IString, String> cubeInfererBuilder = getInferer("cube");
    Inferer<IString, String> cubeDecoder = cubeInfererBuilder.build();
    List<RichTranslation<IString, String>> cubeTranslations = cubeDecoder.nbest
        (IString.getIStringSequence(src), 0, new InputProperties(), outputSpace, targets, 5);
    
    for (int i = 0; i < translations.size(); i++) {
      assertEquals(true, translations.get(i).toStringNoLatticeId().equals(cubeTranslations.get(i).toStringNoLatticeId()));
    }
    for (RichTranslation<IString, String> richTranslation : translations) { System.err.println(richTranslation); }
    assertEquals(translations.get(0).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , i would ||| LM: -1.238E3 LinearDistortion: -55 ||| -6.2999E2"), true);
    assertEquals(translations.get(1).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , i would ||| LM: -1.238E3 LinearDistortion: -55 ||| -6.2999E2"), true);
    assertEquals(translations.get(2).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , would ||| LM: -1.2382E3 LinearDistortion: -55 ||| -6.3008E2"), true);
    assertEquals(translations.get(3).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant when , would ||| LM: -1.2382E3 LinearDistortion: -55 ||| -6.3008E2"), true);
    assertEquals(translations.get(4).toString().startsWith("made do just that 余下 must be 依纪 law . 严处 违抗 forbidden , any servant if , i would ||| LM: -1.2383E3 LinearDistortion: -55 ||| -6.3014E2"), true);
  }
  
  @SuppressWarnings("unchecked")
  public static InfererBuilder<IString, String> getInferer(String infererType) throws IOException, CloneNotSupportedException{
    // inferer builder
    InfererBuilder<IString, String> infererBuilder = InfererBuilderFactory.factory(infererType);    
    
    // unknown word
    boolean dropUnknownWords = false; 
    infererBuilder.setFilterUnknownWords(dropUnknownWords);
    
    // featurizer
    String linearDistortion = LinearFutureCostFeaturizer.class.getName();
    String gapType = FeaturizerFactory.GapType.none.name();
    int numPhraseFeatures = Integer.MAX_VALUE;
    CombinedFeaturizer<IString, String> featurizer = FeaturizerFactory.factory(
        FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
        makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER, linearDistortion),
        makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
        makePair(FeaturizerFactory.ARPA_LM_PARAMETER, "kenlm:" + lgModel),
        makePair(FeaturizerFactory.NUM_PHRASE_FEATURES, String.valueOf(numPhraseFeatures)));
    infererBuilder.setIncrementalFeaturizer((CombinedFeaturizer<IString, String>) featurizer.clone());
    
    // phrase generator
    String generatorName = PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR;
    String optionLimit = "20";
    boolean withGaps = false;
    FlatPhraseTable.createIndex(withGaps); // initialized in Phrasal.initStaticMembers
    PhraseGenerator<IString,String> phraseGenerator = PhraseGeneratorFactory.<String>factory(false, generatorName, phraseTable, optionLimit);
    phraseGenerator = new CombinedPhraseGenerator<IString,String>(
        Arrays.asList(phraseGenerator, new UnknownWordPhraseGenerator<IString, String>(dropUnknownWords, FlatPhraseTable.sourceIndex)),
        CombinedPhraseGenerator.Type.STRICT_DOMINANCE, Integer.parseInt(optionLimit));
    infererBuilder.setPhraseGenerator((PhraseGenerator<IString,String>) phraseGenerator.clone());
    
    // scorer
    Counter<String> weights = new ClassicCounter<String>();
    // Initialize according to Moses heuristic
    Set<String> featureNames = Generics.newHashSet(weights.keySet());
    featureNames.addAll(FeatureUtils.BASELINE_DENSE_FEATURES);
    for (String key : featureNames) {
      if (key.startsWith("LM")) {
        weights.setCount(key, 0.5);
      } else if (key.startsWith("WordPenalty")) {
        weights.setCount(key, -1.0);
      } else {
        weights.setCount(key, 0.2);
      }
    }
    System.err.println("weights: " + weights);
    Scorer<String> scorer = ScorerFactory.factory(ScorerFactory.SPARSE_SCORER, weights, null);
    infererBuilder.setScorer(scorer);

    // search heuristic
    RuleFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
        isolatedPhraseFeaturizer, HeuristicFactory.ISOLATED_PHRASE_SOURCE_COVERAGE);
    infererBuilder.setSearchHeuristic((SearchHeuristic<IString, String>) heuristic.clone());
    
    // recombination Filter
    String recombinationMode = RecombinationFilterFactory.CLASSIC_RECOMBINATION;
    RecombinationFilter<Derivation<IString, String>> filter = RecombinationFilterFactory
        .factory(recombinationMode, featurizer.getNestedFeaturizers());
    infererBuilder.setRecombinationFilter((RecombinationFilter<Derivation<IString, String>>) filter.clone());
    
    return infererBuilder;
  }
}
