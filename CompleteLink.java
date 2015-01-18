import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class CompleteLink
{
	
	static int kClusters = 30;	
	static int kValue = 0;
	
	public static LinkedHashMap<String, Double> completeLinkScore(HashMap<String, Double> atcScore, HashMap<String, HashMap<String, Double>> docTfIdfFull, MAPEvaluation.CLINK cLinkType)
	{
		LinkedHashMap<String, Double> cLinkScore = new LinkedHashMap<String, Double>();
		LinkedHashMap<String, ArrayList<String>> initialClusters = new LinkedHashMap<String, ArrayList<String>>();
		
		//Get the intial set of 30 clusters
		initialClusters = CompleteLink.getInitialClusters(atcScore, initialClusters);
		
		cLinkScore = CompleteLink.getTopDocs(initialClusters,docTfIdfFull,cLinkScore,atcScore,cLinkType);
		
		//Add the rest 70 document rank as it is
		cLinkScore = CompleteLink.getBottomDocs(atcScore, cLinkScore);
	
		return cLinkScore;
		
	}

	private static LinkedHashMap<String, ArrayList<String>> getInitialClusters(
			HashMap<String, Double> atcScore,
			LinkedHashMap<String, ArrayList<String>> initialClusters) {

		int i = 1;
		for ( String key : atcScore.keySet() )
		{
			if ( i <= kClusters)
			{
				ArrayList<String> cluster = new ArrayList<String>();
				cluster.add(key);
				initialClusters.put("Cluster"+ String.valueOf(i), cluster);
			}
			i++;
		}
		return initialClusters;
		
	}

	private static LinkedHashMap<String, Double> getTopDocs(
			LinkedHashMap<String, ArrayList<String>> initialClusters,
			HashMap<String, HashMap<String, Double>> docTfIdfFull,
			LinkedHashMap<String, Double> cLinkScore,
			HashMap<String, Double> atcScore, MAPEvaluation.CLINK cLinkType) {
		
		if (cLinkType == MAPEvaluation.CLINK.HIGHEST)
			kValue = 20;
			else
				kValue = 10;
		
		//Loop till K clusters
		int loopClusters = kClusters;
		while(loopClusters > kValue)
		{
			String candidateA = null, candidateB = null; 
			double minSim = Double.MAX_VALUE, simAB = 0.0; 
			
			for(String keyA: initialClusters.keySet()){
				for(String keyB: initialClusters.keySet()){
					if (keyA != keyB){
						//Find the least similar documents between clusters
						simAB = findMaxbetweenClusters(keyA,keyB,initialClusters,docTfIdfFull);
						//See if the similarity of the ablove clusters is the minimum
						if (minSim > simAB){
						candidateA = keyA;
						candidateB = keyB;
						minSim = simAB;
						}
					}
				}
			}
			//Combine the clusters from the complete linkage
			ArrayList<String> clusterA = initialClusters.get(candidateA);
			ArrayList<String> clusterB = initialClusters.get(candidateB);
			for ( String doc: clusterB){
				clusterA.add(doc);
			}
			loopClusters--;
			clusterB = null;
			initialClusters.put(candidateA,clusterA);
			initialClusters.put(candidateB, null);
			
		}
		
		//ReRank the top 30 documents

			for ( String atcDoc : atcScore.keySet() ){
				for(String clusterKey: initialClusters.keySet()){
					ArrayList<String> clusterDocs = initialClusters.get(clusterKey);
					if (clusterDocs!= null && clusterDocs.contains(atcDoc)){
						if (cLinkType == MAPEvaluation.CLINK.HIGHEST){
							for (String doc: clusterDocs){  
									if (!cLinkScore.containsKey(doc))
											cLinkScore.put(doc, atcScore.get(atcDoc));
								}
						}
						else{		
						double avgSimilarity = 0.0; 
						for (String doc: clusterDocs)  
							avgSimilarity += atcScore.get(doc);
							avgSimilarity = (double)avgSimilarity/clusterDocs.size();
						for (String doc: clusterDocs){  
							if (!cLinkScore.containsKey(doc))
									cLinkScore.put(doc, avgSimilarity);
								}						
							}
					}
				}
			}
		
		return cLinkScore;
	}	
	
	
	private static double findMaxbetweenClusters(String keyA, String keyB,
			LinkedHashMap<String, ArrayList<String>> initialClusters, 
			HashMap<String, HashMap<String, Double>> docTfIdfFull) {
		
		double maxSim = 0.0, simAB = 0.0;
		ArrayList<String> clusterA = initialClusters.get(keyA);
		ArrayList<String> clusterB = initialClusters.get(keyB);
		if (clusterA != null && clusterB != null ){
		for( String docA : clusterA){
			for (String docB : clusterB ){
			simAB = CompleteLink.calculateSimilarity(docA,docB,docTfIdfFull);
			if (simAB > maxSim)
				maxSim = simAB;
				}
			}
		}
		if (maxSim != 0.0)
			return maxSim;
		
		return Double.MAX_VALUE;
			
		}

	private static Double calculateSimilarity(String doc1, String doc2,
			HashMap<String, HashMap<String, Double>> docTfIdfFull) {
			
		double simScore = 0.0;
        HashMap<String, Double> doc1TfIdfMap = docTfIdfFull.get(doc1);
        HashMap<String, Double> doc2TfIdfMap = docTfIdfFull.get(doc2);
		
        for (String term : doc1TfIdfMap.keySet()){
        	if ( doc2TfIdfMap.containsKey(term)){  
        		simScore += doc1TfIdfMap.get(term) * doc2TfIdfMap.get(term); 
        	}
        }
        return ((Double)1.0/simScore);		
	}

	private static LinkedHashMap<String, Double> getBottomDocs(
			HashMap<String, Double> atcScore, LinkedHashMap<String, Double> cLinkScore) {
		
		int i = 1;
		for ( String aScore : atcScore.keySet() )
		{
			if ( i > kClusters)
			{
				cLinkScore.put(aScore, atcScore.get(aScore));
			}
			i++;
		}
		return cLinkScore;	
		
	}	
}