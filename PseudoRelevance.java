import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class PseudoRelevance {
    static String RELEVANCE_FILE_CACM = "data/cacm_processed.rel";
    static String RELEVANCE_FILE_MEDLAR = "data/med_processed.rel";

    public static HashMap<String, Double> getQueryVectorWithRocchioWeightsWithoutRelevance
            (HashMap<String, HashMap<String, Integer>> directIndexMap,
             HashMap<String, Double> topSortedDocScoreMap,
             HashMap<String, Integer> queryVector, HashMap<String, Double> queryTfIdfMap,
             HashMap<String, HashMap<String, Double>> docTfIdfFull,
             double A, double B, int K) {
        double queryNormalizedCoeff = getNormalizedCoeff(queryTfIdfMap);

        ArrayList<String> topSevenDocList = new ArrayList<String>();
        int breakCount = 0;
        for(String document : topSortedDocScoreMap.keySet()) {
            if(breakCount<7) {
                topSevenDocList.add(document);
            } else {
                break;
            }
            breakCount++;
        }

        Set<String> uniqueTerms = getUniqueTerms(directIndexMap, topSevenDocList);
        uniqueTerms.addAll(queryVector.keySet());

        HashMap<String,Double> queryATCMap = new HashMap<String, Double>();
        for(String term :uniqueTerms) {
            if(queryVector.containsKey(term)) {
                queryATCMap.put(term, queryTfIdfMap.get(term)*queryNormalizedCoeff);
            } else {
                queryATCMap.put(term, 0.0);
            }
        }

        HashMap<String, HashMap<String, Double>> documentATCMap = new HashMap<String, HashMap<String, Double>>();
        for(String document : topSevenDocList) {
            HashMap<String, Double> innerMap = new HashMap<String, Double>();
            for(String term : uniqueTerms) {
                if(docTfIdfFull.get(document).containsKey(term)) {
                    innerMap.put(term, docTfIdfFull.get(document).get(term));
                } else {
                    innerMap.put(term,0.0);
                }
            }
            documentATCMap.put(document, innerMap);
        }

        //double A = 4.0;
        //double B = 36.0;
        HashMap<String,Double> rocchioWeightMap = new HashMap<String, Double>();
        for(String term : uniqueTerms) {
            Double first = A*queryATCMap.get(term);
            double scoreSum = 0.0;
            for(String document : topSevenDocList) {
                scoreSum += documentATCMap.get(document).get(term);
            }
            Double rocchioWeight = first + B*(1.0/7.0)*scoreSum;
            rocchioWeightMap.put(term, rocchioWeight);
        }

        HashMap<String, Double> sortedRocchioMap = sortRocchioScoreMap(rocchioWeightMap);
        //int K = 20;
        List<String> termsToAppend = new ArrayList<String>();
        int i =0;
        for(String term : sortedRocchioMap.keySet()) {
            if(i<K) {
                if(!queryVector.keySet().contains(term) && sortedRocchioMap.get(term) != 0.0) {
                    termsToAppend.add(term);
                    i++;
                }
            } else {
                break;
            }
        }
        HashMap<String, Double> finalQueryVector = new HashMap<String, Double>();
        for(String term : queryVector.keySet()) {
            finalQueryVector.put(term,sortedRocchioMap.get(term));
        }
        for(String term :termsToAppend) {
            finalQueryVector.put(term,sortedRocchioMap.get(term));
        }

        return finalQueryVector;
    }

    public static HashMap<String, Double> getQueryVectorWithRocchioWeightsWithRelevance
            (HashMap<String, HashMap<String, Integer>> directIndexMap,
             HashMap<String, Double> topSortedDocScoreMap,
             HashMap<String, Integer> queryVector, HashMap<String, Double> queryTfIdfMap,
             HashMap<String, HashMap<String, Double>> docTfIdfFull,
             Integer queryNumber,
             double A, double B, double C, int K, MAPEvaluation.DOCTYPE docType) {

        double queryNormalizedCoeff = getNormalizedCoeff(queryTfIdfMap);
        ArrayList<String> topSevenDocList = new ArrayList<String>();
        int breakCount = 0;
        for(String document : topSortedDocScoreMap.keySet()) {
            if(breakCount<7) {
                topSevenDocList.add(document);
            } else {
                break;
            }
            breakCount++;
        }

        Map<Integer, LinkedHashSet<String>> relResultsMap;
        if(docType.equals(MAPEvaluation.DOCTYPE.MEDLAR)) {
            relResultsMap = loadAnswers(RELEVANCE_FILE_MEDLAR);
        } else {
            relResultsMap = loadAnswers(RELEVANCE_FILE_CACM);
        }

        ArrayList<String> relevantDocList = new ArrayList<String>();
        ArrayList<String> nonRelevantDocList = new ArrayList<String>();

        HashSet<String> relDocSet = relResultsMap.get(queryNumber);
        for(String document : topSevenDocList) {
            if(relDocSet.contains(document)) {
                relevantDocList.add(document);
            } else {
                nonRelevantDocList.add(document);
            }
        }

        String topRankedRelevantDocument = "";
        String topRankedNonRelevantDocument = "";

        if(!relevantDocList.isEmpty()) {
            topRankedRelevantDocument = getTopRanked(relevantDocList, topSortedDocScoreMap);
        }
        if(!nonRelevantDocList.isEmpty()) {
            topRankedNonRelevantDocument = getTopRanked(nonRelevantDocList, topSortedDocScoreMap);
        }

        Set<String> uniqueTerms = getUniqueTerms(directIndexMap, topSevenDocList);
        uniqueTerms.addAll(queryVector.keySet());

        HashMap<String,Double> queryATCMap = new HashMap<String, Double>();
        for(String term :uniqueTerms) {
            if(queryVector.containsKey(term)) {
                queryATCMap.put(term, queryTfIdfMap.get(term)*queryNormalizedCoeff);
            } else {
                queryATCMap.put(term, 0.0);
            }
        }

        HashMap<String, HashMap<String, Double>> documentATCMap = new HashMap<String, HashMap<String, Double>>();
        for(String document : topSevenDocList) {
            HashMap<String, Double> innerMap = new HashMap<String, Double>();
            for(String term : uniqueTerms) {
                if(docTfIdfFull.get(document).containsKey(term)) {
                    innerMap.put(term, docTfIdfFull.get(document).get(term));
                } else {
                    innerMap.put(term,0.0);
                }
            }
            documentATCMap.put(document, innerMap);
        }

        //double A = 4.0;
        //double B = 8.0;
        //double C = 4.0;
        HashMap<String,Double> rocchioWeightMap = new HashMap<String, Double>();
        for(String term : uniqueTerms) {
            Double first = A*queryATCMap.get(term);             //A*qj
            double relScore = 0.0;
            if(!topRankedRelevantDocument.isEmpty()) {
                if(documentATCMap.get(topRankedRelevantDocument).containsKey(term)) {
                    relScore = documentATCMap.get(topRankedRelevantDocument).get(term);
                } else {
                    relScore = 0.0;
                }
            }


            double nonRelScore = 0.0;
            if(!topRankedNonRelevantDocument.isEmpty()) {
                if(documentATCMap.get(topRankedNonRelevantDocument).containsKey(term)) {
                    nonRelScore = documentATCMap.get(topRankedNonRelevantDocument).get(term);
                } else {
                    nonRelScore = 0.0;
                }
            }


            Double second = topRankedRelevantDocument.isEmpty() ? 0.0 : B*relScore ;            //B*(1/|Rel|)*Sum(dij)
            Double third = topRankedNonRelevantDocument.isEmpty() ?  0.0 : C*nonRelScore ;   //C*(1/|Non Rel|)*Sum(dij)
            Double rocchioWeight = first + second - third ;
            rocchioWeightMap.put(term, rocchioWeight);
        }

        HashMap<String, Double> sortedRocchioMap = sortRocchioScoreMap(rocchioWeightMap);
        //int K = 5;
        List<String> termsToAppend = new ArrayList<String>();
        int i =0;
        for(String term : sortedRocchioMap.keySet()) {
            if(i<K) {
                if(!queryVector.keySet().contains(term) && sortedRocchioMap.get(term) != 0.0) {  //add top K non zero terms not present in query
                    termsToAppend.add(term);
                    i++;
                }
            } else {
                break;
            }
        }
        HashMap<String, Double> finalQueryVector = new HashMap<String, Double>();
        for(String term : queryVector.keySet()) {
            finalQueryVector.put(term,sortedRocchioMap.get(term));
        }
        for(String term :termsToAppend) {
            finalQueryVector.put(term,sortedRocchioMap.get(term));
        }

        return finalQueryVector;
    }

    private static String getTopRanked(List<String> docList, HashMap<String, Double> topSortedDocScoreMap) {
        String topRankedDocument = docList.get(0);
        for(int i=1;i<docList.size();i++) {
            if(topSortedDocScoreMap.get(docList.get(i)) > topSortedDocScoreMap.get(topRankedDocument)) {
                topRankedDocument = docList.get(i);
            }
        }
        return topRankedDocument;
    }
    private static HashMap<String, Double> sortRocchioScoreMap(Map<String, Double> wt)
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

    private static Set<String> getUniqueTerms(HashMap<String,HashMap<String,Integer>> directIndexMap,ArrayList<String> topSevenDocList ) {
        Set<String> uniqueTerms = new HashSet<String>();
        for(String document : topSevenDocList) {
            uniqueTerms.addAll(directIndexMap.get(document).keySet());
        }
        return uniqueTerms;
    }

    private static double getNormalizedCoeff(HashMap<String,Double> termTfIdfVector) {
        double sumSquareTfidf = 0.0;
        for(String term : termTfIdfVector.keySet()) {
            Double termTfIdf = termTfIdfVector.get(term);
            sumSquareTfidf += termTfIdf*termTfIdf;
        }
        return (1/Math.sqrt(sumSquareTfidf));
    }

    private static Map<Integer, LinkedHashSet<String>> loadAnswers(String filename) {
        HashMap<Integer, LinkedHashSet<String>> queryAnswerMap = new HashMap<Integer, LinkedHashSet<String>>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(
                    new File(filename)));

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                LinkedHashSet<String> answers = new LinkedHashSet<String>();
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

}
