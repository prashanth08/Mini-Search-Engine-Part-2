import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

import java.io.*;
import java.util.*;

public class MAPEvaluation {
    static WEIGHTING variants = WEIGHTING.ATC_ATC;
    static DOCTYPE docType = DOCTYPE.CACM;
    static CLINK cLinkType = CLINK.HIGHEST;
    static String CACMINDEXDIR ="data/index/cacm";
    static String CACMDOCDIR = "data/cacm";
    static String MEDINDEXDIR="data/index/med";
    static String MEDDOCDIR = "data/med";
    static String CACMQUERYFILEPATH = "data/cacm_processed.query";
    static String MEDQUERYFILEPATH = "data/med_processed.query";
    static String STOPWORDFILE = "data/stopwords/stopwords_indri.txt";
    static String RELEVANCE_FILE_CACM = "data/cacm_processed.rel";
    static String RELEVANCE_FILE_MEDLAR = "data/med_processed.rel";
    static int CACM_DOC_SIZE = 3204;
    static int MED_DOC_SIZE = 1033;
    static double ROCCHIO_PARAM_A = 4.0;
    static double ROCCHIO_PARAM_B = 16.0;
    static double ROCCHIO_PARAM_C = 0.0;
    static int ROCCHIO_PARAM_K = 5;
    static FEEDBACK_TYPE feedbackType = FEEDBACK_TYPE.ROCCHIO;


    public static void main(String[] args) throws Exception{
        long startTime = System.currentTimeMillis();
        DirectIndex dIndex = new DirectIndex();
        HashMap<String,HashMap<String,Integer>> directIndexMap;
        Map<Integer, String> queries;
        if(docType.equals(DOCTYPE.MEDLAR)) {
            dIndex.buildIndex(MEDINDEXDIR, MEDDOCDIR, STOPWORDFILE);
            directIndexMap = dIndex.getDirectIndexMap();
            queries = loadQueries(MEDQUERYFILEPATH);
        } else {
            dIndex.buildIndex(CACMINDEXDIR, CACMDOCDIR, STOPWORDFILE);
            directIndexMap = dIndex.getDirectIndexMap();
            queries = loadQueries(CACMQUERYFILEPATH);
        }
        CharArraySet stopwords = readStopFile(STOPWORDFILE);
        HashMap<String,LinkedHashMap<String,Double>> outerMap = new HashMap<String, LinkedHashMap<String,Double>>();
        HashMap<String,LinkedHashMap<String,Double>> rocchioOuterMap = new HashMap<String, LinkedHashMap<String,Double>>();
        HashMap<String,LinkedHashMap<String,Double>> cOuterMap = new HashMap<String, LinkedHashMap<String,Double>>();        
        HashMap<String,Double> idfFull = new HashMap<String, Double>();        
                
        //System.out.println("PROCESSING FOR "+docType.name()+" Collection with "+variants.name()+" weighting and complete link clustering with " + cLinkType + " similarity:" );
        System.out.println("PROCESSING FOR "+docType.name()+" Collection with "+variants.name()+" weighting " );

        HashMap<String,Integer> termMatchMap = new HashMap<String, Integer>();
        
        //Prepare the tf*idf table for the complete document set for better effeciency
        HashMap<String,HashMap<String,Double>> docTfIdfFull = getDocTfIdfFull(directIndexMap,idfFull); 

        for (Integer i : queries.keySet()) {
            String docString = queries.get(i);

            StandardTokenizer src = new StandardTokenizer(new StringReader(docString));
            TokenStream tok = new StandardFilter(src);
            tok = new LowerCaseFilter(tok);
            tok = new StopFilter(tok, stopwords);
            tok = new PorterStemFilter(tok);
            tok.reset();
            CharTermAttribute charTermAttribute = tok.addAttribute(CharTermAttribute.class);

            HashMap<String,Integer> queryVector = new HashMap<String, Integer>();
            while(tok.incrementToken()) {
                String term = charTermAttribute.toString();
                if(!queryVector.containsKey(term)) {
                    queryVector.put(term, 1);
                } else {
                    int count = queryVector.get(term);
                    queryVector.put(term,count+1);
                }
            }

            //document term count index ready
            Set<String> documentSet = directIndexMap.keySet();
            //loop through each document

            HashMap<String,Double> docScoreMap = new HashMap<String, Double>();
            updateTermMatch(queryVector, directIndexMap, termMatchMap);
            double avDl = getAvDl(directIndexMap);

            HashMap<String, Double> queryTfMap = new HashMap<String, Double>();
            HashMap<String, Double> queryIdfMap = new HashMap<String, Double>();
            HashMap<String, Double> queryTfIdfMap = new HashMap<String, Double>();


            for(String term : queryVector.keySet()) {
                double tfQuery = getAugTfValue(queryVector, term);
                double queryIdf = getIdfValue(directIndexMap,idfFull,term);
                queryTfMap.put(term,tfQuery);
                queryIdfMap.put(term,queryIdf);
                queryTfIdfMap.put(term, tfQuery * queryIdf);
            }

            double queryNormalizedCoeff = getNormalizedCoeff(queryTfIdfMap);

            for(String document : documentSet) {
                //get the word count vector
                HashMap<String, Integer> docVector = directIndexMap.get(document);
                double score = 0.0;
                HashMap<String, Double> docTfMap = new HashMap<String, Double>();
                HashMap<String, Double> docIdfMap = new HashMap<String, Double>();
                HashMap<String, Double> docTfIdfMap = new HashMap<String, Double>();


                //for each word in the word vector
                if(variants.equals(WEIGHTING.BM_25)) {
                    double bm25Sum = 0.0;
                    int documentLength = getDocumentLength(document, directIndexMap);

                    for(String term : queryVector.keySet()) {
                        int fi = docVector.containsKey(term)?docVector.get(term):0;
                        int qfi = queryVector.get(term);
                        int N = docType.equals(DOCTYPE.CACM)?CACM_DOC_SIZE: MED_DOC_SIZE;
                        int ni = termMatchMap.get(term);
                        bm25Sum += getBM25Score(documentLength, avDl, ni, N, fi, qfi);
                    }
                    docScoreMap.put(document, bm25Sum);
                } else {
                	if (variants.equals(WEIGHTING.ANN_BPN))
                	{
                    for(String term : docVector.keySet()) {
                        double tfDoc = getAugTfValue(docVector, term);
                        double docIdf = getIdfValue(directIndexMap,idfFull,term);
                        docTfMap.put(term,tfDoc);
                        docIdfMap.put(term, docIdf);
                        docTfIdfMap.put(term, tfDoc * docIdf);
                    }
                	}
                	
                	docTfIdfMap = docTfIdfFull.get(document);

                    for(String queryTerm : queryVector.keySet()) {
                        if(docVector.containsKey(queryTerm)) {                        	
                            switch (variants) {
                                case ATN_ATN:                          	
                                    score+= ((docTfIdfMap.get(queryTerm)) )* (queryTfIdfMap.get(queryTerm));
                                    break;
                                case ANN_BPN:
                                    score+= (docTfMap.get(queryTerm) * queryIdfMap.get(queryTerm));
                                    break;
                                case ATC_ATC:                                  	
                                	score+= (docTfIdfMap.get(queryTerm) ) *
                                            (queryTfIdfMap.get(queryTerm) * queryNormalizedCoeff);
                                    break;
                                case LTN_LTN:
                                	score += ((docTfIdfMap.get(queryTerm)) )* (queryTfIdfMap.get(queryTerm));
                                    break;
                            }
                        }
                    }
                    docScoreMap.put(document, score);
                }
            }
            HashMap<String, Double> sortedDocScoreMap = sortDocScoreMap(docScoreMap);
            LinkedHashMap<String, Double> topSortedDocScoreMap = new LinkedHashMap<String, Double>();
            int breakCount = 0;
            for(String sortedDoc : sortedDocScoreMap.keySet()) {
                if(breakCount==100) {
                    break;
                } else {
                	topSortedDocScoreMap.put(sortedDoc, sortedDocScoreMap.get(sortedDoc));
                }
                breakCount++;
            }
            outerMap.put(String.valueOf(i), topSortedDocScoreMap);
            // Now get modified rocchio query vector, recalculate scores for documents
            HashMap<String, Double> rocchioQueryVector;
            if(feedbackType == FEEDBACK_TYPE.PSEUDO) {
                    rocchioQueryVector =
                        PseudoRelevance.getQueryVectorWithRocchioWeightsWithoutRelevance(directIndexMap, topSortedDocScoreMap, queryVector,
                                    queryTfIdfMap, docTfIdfFull, ROCCHIO_PARAM_A, ROCCHIO_PARAM_B, ROCCHIO_PARAM_K);
            } else {
                    rocchioQueryVector =
                        PseudoRelevance.getQueryVectorWithRocchioWeightsWithRelevance(directIndexMap, topSortedDocScoreMap, queryVector,
                                    queryTfIdfMap, docTfIdfFull, i, ROCCHIO_PARAM_A, ROCCHIO_PARAM_B, ROCCHIO_PARAM_C, ROCCHIO_PARAM_K, docType);
            }

            HashMap<String,Double> rocchioDocScoreMap = new HashMap<String, Double>();
            for(String document : documentSet) {
                double scoreWithRocchioWt = 0.0;
                HashMap<String, Integer> documentVector = directIndexMap.get(document);
                for(String term : rocchioQueryVector.keySet()) {
                    if(documentVector.containsKey(term)) {
                        //dot product between rocchio query vector and document tfidf
                        scoreWithRocchioWt += (docTfIdfFull.get(document).get(term)) * (rocchioQueryVector.get(term));
                    }
                }
                rocchioDocScoreMap.put(document, scoreWithRocchioWt);
            }


            HashMap<String, Double> sortedRocchioScoreMap = sortDocScoreMap(rocchioDocScoreMap);
            LinkedHashMap<String, Double> topSortedRocchioDocScoreMap = new LinkedHashMap<String, Double>();
            int breakRCount = 0;
            for(String sortedRocchioDoc : sortedRocchioScoreMap.keySet()) {
                if(breakRCount==100) {
                    break;
                } else {
                    topSortedRocchioDocScoreMap.put(sortedRocchioDoc, sortedRocchioScoreMap.get(sortedRocchioDoc));
                }
                breakRCount++;
            }
            rocchioOuterMap.put(String.valueOf(i), topSortedRocchioDocScoreMap);

            HashMap<String, Double> cDocScoreMap = CompleteLink.completeLinkScore(topSortedDocScoreMap, docTfIdfFull, cLinkType);
            cDocScoreMap = sortDocScoreMap(cDocScoreMap);
            LinkedHashMap<String, Double> cSortedDocScoreMap = new LinkedHashMap<String, Double>(cDocScoreMap);
            cOuterMap.put(String.valueOf(i), cSortedDocScoreMap);


            System.out.print(".");
        }
      
        System.out.println("\n" + "ATC.ATC");
        calculatePrecision(outerMap);

        System.out.println("\nWith Cluster " +cLinkType + " similarity");
        calculatePrecision(cOuterMap);

String relevancePrintString = (feedbackType == FEEDBACK_TYPE.PSEUDO)? "With Pseudo Relevance Feedback" : "With Rocchio Relevance Feedback";
        System.out.println("\n"+ relevancePrintString);
        calculatePrecision(rocchioOuterMap);


        long stopTime = System.currentTimeMillis();
        System.out.println("Time taken:"+ ((stopTime - startTime)/1000)%60 + " seconds");
    }

    private static HashMap<String, HashMap<String, Double>> getDocTfIdfFull(HashMap<String, HashMap<String, Integer>> directIndexMap,
                                                                            HashMap<String, Double> idfFull) {
        HashMap<String, HashMap<String, Double>> docTfIdfFull = new HashMap<String, HashMap<String, Double>>();
        Set<String> documentSet = directIndexMap.keySet();
        //loop through each document
        for (String document : documentSet) {
            HashMap<String, Integer> docVector = directIndexMap.get(document);
            HashMap<String, Double> docTfIdfMap = new HashMap<String, Double>();
            for (String term : docVector.keySet()) {
                double tfDoc = getAugTfValue(docVector, term);
                double docIdf = getIdfValue(directIndexMap, idfFull, term);
                docTfIdfMap.put(term, tfDoc * docIdf);
            }
            if (variants.equals(WEIGHTING.ATC_ATC)) {
                double docNormalizedCoeff = getNormalizedCoeff(docTfIdfMap);
                    for (String d : docTfIdfMap.keySet()) {
                        docTfIdfMap.put(d, docTfIdfMap.get(d) * docNormalizedCoeff);
                    }
                docTfIdfFull.put(document, docTfIdfMap);
            }
        }
        return docTfIdfFull;
    }

    public static HashMap<String, Double> sortDocScoreMap(Map<String, Double> wt)
    {
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(wt.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });
        HashMap<String, Double> sortedMap = new LinkedHashMap<String, Double>();
        for (Iterator<Map.Entry<String, Double>> it = list.iterator(); it.hasNext();) {
            Map.Entry<String, Double> entry = it.next();
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private static double getNormalizedCoeff(HashMap<String,Double> termTfIdfVector) {
        double sumSquareTfidf = 0.0;
        for(String term : termTfIdfVector.keySet()) {
            Double termTfIdf = termTfIdfVector.get(term);
            sumSquareTfidf += termTfIdf*termTfIdf;
        }
        return (1/Math.sqrt(sumSquareTfidf));
    }

    private static void updateTermMatch(HashMap<String, Integer> queryVector,
                  HashMap<String, HashMap<String, Integer>> directIndexMap, HashMap<String, Integer> termMatchMap) {
        for(String term : queryVector.keySet()) {
            if(!termMatchMap.containsKey(term)) {
                int niCount = 0;
                for(String doc : directIndexMap.keySet()) {
                    if(directIndexMap.get(doc).containsKey(term)) {
                        niCount++;
                    }
                }
                termMatchMap.put(term, niCount);
            }
        }
    }

    private static double getAvDl(HashMap<String,HashMap<String,Integer>> directIndexMap) {
        int totalDocLength = 0;
        for(String document : directIndexMap.keySet()) {
            totalDocLength += getDocumentLength(document, directIndexMap);
        }
        return ((double)totalDocLength/directIndexMap.size());
    }

    private static int getDocumentLength(String docName, HashMap<String,HashMap<String,Integer>> directIndexMap) {
        int docLength = 0;
        HashMap<String, Integer> termFrequencyMap = directIndexMap.get(docName);
        for(String term: termFrequencyMap.keySet()) {
            docLength += termFrequencyMap.get(term).intValue();
        }
        return docLength;
    }

    private static double getBM25Score(int dl, double avdl, int ni, int N,int fi, int qfi) {
        avdl = 51.4497;
    	double b = 0.75;
        double k1 = 1.2;
        double k2 = 100;
        double K = k1*((1-b) + b*(dl/avdl));
        double bm25Score = Math.log ((1/((ni+0.5)/(N - ni + 0.5)))) * (((k1+1)*fi) / (K+fi)) * (((k2+1)*qfi) / (k2+qfi));
        return bm25Score;
    }

    private static Map<Integer, String> loadQueries(String filename) {
        HashMap<Integer, String> queryIdMap = new HashMap<Integer, String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(
                    new File(filename)));
        } catch (FileNotFoundException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }

        String line;
        try {
            while ((line = in.readLine()) != null) {
                int pos = line.indexOf(',');
                queryIdMap.put(Integer.parseInt(line.substring(0, pos)), line
                        .substring(pos + 1));
            }
        } catch(IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch(IOException e) {
                System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            }
        }
        return queryIdMap;
    }

    private static void calculatePrecision(HashMap<String,LinkedHashMap<String,Double>> outerMap) {
        Map<Integer, HashSet<String>> relResultsMap;
        double avgSumAllQueries = 0.0;
        double noRel;
        double avgSum;
        int i;
        if(docType.equals(DOCTYPE.MEDLAR)) {
            relResultsMap = loadAnswers(RELEVANCE_FILE_MEDLAR);
        } else {
            relResultsMap = loadAnswers(RELEVANCE_FILE_CACM);
        }

        for(String query:outerMap.keySet()) {
            HashMap<String, Double> docScoreMap = outerMap.get(query);

            Set<String> relAnswers = relResultsMap.get(Integer.parseInt(query));
            noRel = 0.0;
            avgSum = 0.0;
            i=1;
            for(String doc : docScoreMap.keySet()) {
                if(relAnswers.contains(doc)) {
                    noRel++;
                    avgSum+=noRel/i;
                }
                i++;
            }
            avgSumAllQueries += avgSum/relAnswers.size();
            //print for getting individual query precision
            //System.out.println(String.valueOf(query) + " " + avgSum/relAnswers.size());
        }
        System.out.println("************************************************************");
        System.out.println("MEAN AVERAGE PRECISION:" +avgSumAllQueries/outerMap.size());
        System.out.println("************************************************************");
    }

    private static Map<Integer, HashSet<String>> loadAnswers(String filename) {
        HashMap<Integer, HashSet<String>> queryAnswerMap = new HashMap<Integer, HashSet<String>>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(
                    new File(filename)));

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                HashSet<String> answers = new HashSet<String>();
                for (int i = 1; i < parts.length; i++) {
                    answers.add(parts[i]);
                }
                queryAnswerMap.put(Integer.parseInt(parts[0]), answers);
            }
        } catch(IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch(IOException e) {
                System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            }
        }
        return queryAnswerMap;
    }

    private static double getAugTfValue(HashMap<String, Integer> inputVector, String term) {   	
    	if(inputVector.containsKey(term)) {
            if(variants == WEIGHTING.LTN_LTN )
            {
                int maxCount = Collections.max(inputVector.values());
                double tfValue = 1 + ((double)(Math.log(inputVector.get(term))));
                return tfValue; 
            }
            int maxCount = Collections.max(inputVector.values());
            double tfValue = 0.5+0.5*((double)(inputVector.get(term))/(double)maxCount);
            return tfValue;
        } else {
            return 0.0;
        }
    }

    private static double getIdfValue(HashMap<String,HashMap<String,Integer>> documentTermFrequencyMap,
                                      HashMap<String,Double> fullDocIdfMap, String term) {
        if(!fullDocIdfMap.containsKey(term))
        {
            int match = 0;
            Set<String> documentSet = documentTermFrequencyMap.keySet();
            for(String document : documentSet) {
                HashMap<String, Integer> innerMap = documentTermFrequencyMap.get(document);
                if(innerMap.containsKey(term)) {
                    match++;
                }
            }
            if(match == 0) {
                fullDocIdfMap.put(term, 0.0);
                return 0.0;
            }

            if(variants.equals(WEIGHTING.ANN_BPN)) {
                fullDocIdfMap.put(term, Math.max(0.0,
                        Math.log(((double) documentTermFrequencyMap.size() - (double) match) / (double) match)));
                return Math.max(0.0,
                        Math.log(((double) documentTermFrequencyMap.size() - (double) match) / (double) match));
            } else {
                fullDocIdfMap.put(term, Math.log(((double) documentTermFrequencyMap.size() / (double) match)));
                return Math.log(((double) documentTermFrequencyMap.size() / (double) match));
            }
        } else {
            return fullDocIdfMap.get(term);
        }
    }

    public static CharArraySet readStopFile(String filename) {
        CharArraySet stopWordsSet;
        ArrayList<String> stopWords = new ArrayList<String>();
        BufferedReader in;
        try {
            in = new BufferedReader(new FileReader(new File(filename))); // Reads the file line by line
            String line;

            while ((line = in.readLine()) != null) {
                stopWords.add(line);
            }
            in.close();

        } catch(IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }
        stopWordsSet = new CharArraySet(stopWords,false);
        return stopWordsSet;
    }
    //weighting schemes
    enum WEIGHTING {
        ATC_ATC,
        ATN_ATN,
        ANN_BPN,
        LTN_LTN,
        BM_25,
    }

    //document type
    enum DOCTYPE {
        CACM,
        MEDLAR
    }
    
  //Complete Linkage Type
    enum CLINK {
        HIGHEST,
        AVERAGE
    }

    //Feedback type rocchio or pseudo relevance
    enum FEEDBACK_TYPE {
        ROCCHIO,
        PSEUDO
    }
    
}
