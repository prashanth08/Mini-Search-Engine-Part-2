****************************
Alok Kumar Prusty (akp77)
Nikhil Anand Navali (nn259)
Prashanth Basappa (pb476)
*****************************

Execution:
Run the file MAPEvaluation.java with by setting following parameters in the program: 

variants should be set to "WEIGHTING.ATC_ATC" as we are only building on top of ATC.ATC weighting from project one
The docType is used to specify the collection to be run on. For CACM it takes the enum as DOCTYPE.CACM and for medlar it takes the value as DOCTYPE.MEDLAR
cLinkType is used to define the Complete link clustering type whether it is an average similarity or highest similarity. That can be done with the enum CLINK.HIGHEST or CLINK.AVERAGE
For question 1 set the feedbackType to FEEDVBACK_TYPE.PSEUDO and for question 3 set it to FEEDBACK_TYPE.ROCCHIO
A,B,C,K are declared as static constants which can be configured.K accepts integer values where as A,B and C takes double values.

Rest all comments are included as part of code.

Once these values are set, run the file and the MAP values are displayed in the console. One typical output looks like below.

....................................................
ATC.ATC
************************************************************
MEAN AVERAGE PRECISION:0.30954792621944
************************************************************

With Cluster HIGHEST similarity
************************************************************
MEAN AVERAGE PRECISION:0.3030215250892239
************************************************************

With Rocchio Relevance Feedback
************************************************************
MEAN AVERAGE PRECISION:0.35684756136545914
************************************************************
Time taken:8 seconds


Other Files 
===========
DirectIndex.java is same as old which builds the direct document index
CompleteLink.java takes care of clustering logic
PseudoRelevance.java takes care of both pseudo relevance and rocchio relevance.