/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package experiments;


import evaluation.tuning.ParameterSpace;
import machine_learning.classifiers.RidgeClassifierCV;
import machine_learning.classifiers.ensembles.ContractRotationForest;
import machine_learning.classifiers.ensembles.EnhancedRotationForest;
import machine_learning.classifiers.tuned.TunedClassifier;
import machine_learning.classifiers.tuned.TunedSVM;
import tsml.classifiers.EnhancedAbstractClassifier;
import tsml.classifiers.distance_based.distances.dtw.DTWDistance;
import tsml.classifiers.distance_based.distances.ed.EDistance;
import tsml.classifiers.distance_based.distances.erp.ERPDistance;
import tsml.classifiers.distance_based.distances.lcss.LCSSDistance;
import tsml.classifiers.distance_based.distances.msm.MSMDistance;
import tsml.classifiers.distance_based.distances.wdtw.WDTWDistance;
import tsml.classifiers.distance_based.elastic_ensemble.ElasticEnsemble;
import tsml.classifiers.distance_based.knn.KNN;
import tsml.classifiers.distance_based.proximity.ProximityForest;
import tsml.classifiers.early_classification.*;
import tsml.classifiers.kernel_based.Arsenal;
import tsml.classifiers.hybrids.Catch22Classifier;
import tsml.classifiers.hybrids.HIVE_COTE;
import tsml.classifiers.dictionary_based.*;
import tsml.classifiers.dictionary_based.boss_variants.BOSSC45;
import tsml.classifiers.dictionary_based.SpatialBOSS;
import tsml.classifiers.dictionary_based.boss_variants.BoTSWEnsemble;
import tsml.classifiers.distance_based.*;
import tsml.classifiers.kernel_based.ROCKETClassifier;
import tsml.classifiers.interval_based.*;
import tsml.classifiers.legacy.COTE.FlatCote;
import tsml.classifiers.legacy.elastic_ensemble.DTW1NN;
import tsml.classifiers.multivariate.*;
import tsml.classifiers.shapelet_based.*;
import tsml.classifiers.shapelet_based.FastShapelets;
import tsml.classifiers.shapelet_based.LearnShapelets;
import tsml.classifiers.shapelet_based.ShapeletTree;
import tsml.transformers.*;
import weka.core.EuclideanDistance;
import weka.core.Randomizable;
import machine_learning.classifiers.ensembles.CAWPE;
import machine_learning.classifiers.PLSNominalClassifier;
import machine_learning.classifiers.kNN;
import machine_learning.classifiers.tuned.TunedXGBoost;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.meta.RotationForest;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;

import java.util.Arrays;
import java.util.HashSet;

/**
 *
 * @author James Large (james.large@uea.ac.uk) and Tony Bagnall
 */
public class ClassifierLists {

    //All implemented classifiers in tsml
    //<editor-fold defaultstate="collapsed" desc="All univariate time series classifiers">
    public static String[] allUnivariate={
//Distance Based
            "DTW","DTWCV", "EE","LEE","ApproxElasticEnsemble","ProximityForest","FastElasticEnsemble",
            "DD_DTW","DTD_C","CID_DTW","NN_CID",
//Dictionary Based
            "BOP", "SAXVSM", "SAX_1NN", "BOSS", "cBOSS", "S-BOSS","BoTSWEnsemble","WEASEL",
//Interval Based
            "LPS","TSF",
//Frequency Based
            "RISE",
//Shapelet Based
            "FastShapelets","LearnShapelets","ShapeletTransformClassifier","ShapeletTreeClassifier","STC",
//Hybrids
            "HiveCoteAlpha","FlatCote","TS-CHIEF","HIVE-COTEv1"
};
    //</editor-fold>
    public static HashSet<String> allClassifiers=new HashSet<String>( Arrays.asList(allUnivariate));

    /**
     * DISTANCE BASED: classifiers based on measuring the distance between two classifiers
     */
    public static String[] distance= {
//Nearest Neighbour with distances
        "1NN-ED","1NN-DTW","1NN-DTWCV", "DD_DTW","DTD_C","CID_DTW","NN_CID", "NN_ShapeDTW", "1NN-DTW_New","1NN-DTW-Jay","1NN-MSM","1NN-ERP","1NN-LCSS","1NN-WDTW",
//EE derivatives
            "ElasticEnsemble","EE","LEE","ApproxElasticEnsemble","FastElasticEnsemble",
//Tree based
            "ProximityForest","PF"
    };
    public static HashSet<String> distanceBased=new HashSet<String>( Arrays.asList(distance));
    private static Classifier setDistanceBased(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c = null;
        int fold=exp.foldId;
        switch(classifier) {
            case "1NN-ED":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new EDistance());
                break;
            case "1NN-DTW":
                c = new DTW_kNN();
                ((DTW_kNN)c).optimiseWindow(false);
                ((DTW_kNN)c).setMaxR(1.0);
                break;
            case "1NN-DTW-Jay":
                c = new DTW1NN();
                break;
            case "1NN-DTW_New":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new DTWDistance());
                break;
            case "1NN-MSM":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new MSMDistance());
                break;
            case "1NN-ERP":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new ERPDistance());
                break;
            case "1NN-LCSS":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new LCSSDistance());
                break;
            case "1NN-WDTW":
                c = new KNN();
                ((KNN) c).setDistanceMeasure(new WDTWDistance());
                break;
            case "1NN-DTWCV":
                c = new DTWCV();
                break;
            case "EE":
                c = ElasticEnsemble.CONFIGS.get(classifier).build();
                break;
            case "LEE":
                c = ElasticEnsemble.CONFIGS.get(classifier).build();
                break;
            case "ApproxElasticEnsemble":
                c = new ApproxElasticEnsemble();
                break;
            case "FastElasticEnsemble":
                c=new FastElasticEnsemble();
                break;
            case "DD_DTW":
                c=new DD_DTW();
                break;
            case "DTD_C":
                c=new DTD_C();
                break;
            case "CID_DTW":
                c=new NN_CID();
                ((NN_CID)c).useDTW();
                break;
            case "NN_CID":
                c = new NN_CID();
                break;

            case "ProximityForest":
                c = new ProximityForest();
                ((ProximityForest)c).setTrainEstimateMethod("OOB");

                break;
            case "NN_ShapeDTW_Raw":
                c=new ShapeDTW_1NN(30,null,false,null);
                break;
            case "NN_ShapeDTW_PAA":
                PAA p = new PAA();
                p.setNumIntervals(5);
                c=new ShapeDTW_1NN(30,p,false,null);
                break;
            case "NN_ShapeDTW_DWT":
                DWT dwt = new DWT();
                c=new ShapeDTW_1NN(30,dwt,false,null);
                break;
            case "NN_ShapeDTW_Der":
                Derivative der = new Derivative();
                c=new ShapeDTW_1NN(30,der,false,null);
                break;
            case "NN_ShapeDTW_Slope":
                Slope s = new Slope(5);
                c=new ShapeDTW_1NN(30,s,false,null);
                break;
            case "NN_ShapeDTW_Hog":
                HOG1D h = new HOG1D();
                c=new ShapeDTW_1NN(30,h,false,null);
                break;
            case "NN_ShapeDTW_Comp":
                DWT dwt2 = new DWT();
                HOG1D h2 = new HOG1D();
                c=new ShapeDTW_1NN(30,dwt2,true,h2);
                break;
            case "SVM_ShapeDTW_Poly":
                c=new ShapeDTW_SVM();
                break;
            case "SVM_ShapeDTW_RBF":
                c=new ShapeDTW_SVM(30, ShapeDTW_SVM.KernelType.RBF);
                break;
            default:
                System.out.println("Unknown distance based classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between array distance and the switch statement ");
                throw new UnsupportedOperationException("Unknown distance based  classifier "+classifier+" should not be able to get here. "
                        + "There is a mismatch between array distance and the switch statement.");

        }
        return c;
    }
    /**
     * DICTIONARY BASED: classifiers based on counting the occurrence of words in series
     */
    public static String[] dictionary= {"BOP","SAXVSM","SAX_1NN","BOSS","cBOSS","S-BOSS","BoTSWEnsemble","WEASEL",
            "TDE"};
    public static HashSet<String> dictionaryBased=new HashSet<String>( Arrays.asList(dictionary));
    private static Classifier setDictionaryBased(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c;
        int fold=exp.foldId;
        switch(classifier) {
            case "BOP":
                c=new BagOfPatternsClassifier();
                break;
            case "SAXVSM":
                c=new SAXVSM();
                break;
            case "SAX_1NN":
                c=new SAXVSM();
                break;
            case "BOSS":
                c=new BOSS();
                break;
            case "cBOSS":
                c = new cBOSS();
                break;
            case "BOSSC45":
                c = new BOSSC45();
                break;
            case "S-BOSS":
                c = new SpatialBOSS();
                break;
            case "BoTSWEnsemble":
                c = new BoTSWEnsemble();
                break;
            case "WEASEL":
                c = new WEASEL();
                break;
            case "TDE":
                c = new TDE();
                break;
            default:
                System.out.println("Unknown dictionary based classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between array dictionary and the switch statement ");
                throw new UnsupportedOperationException("Unknown dictionary based  classifier "+classifier+" should not be able to get here."
                        + "There is a mismatch between array dictionary and the switch statement ");

        }
        return c;
    }

    /**
    * INTERVAL BASED: classifiers that form multiple intervals over series and summarise
    */
    public static String[] interval= {"LPS","TSF","RISE","CIF","STSF","DrCIF"};
    public static HashSet<String> intervalBased=new HashSet<String>( Arrays.asList(interval));
    private static Classifier setIntervalBased(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c;
        int fold=exp.foldId;
        switch(classifier) {
            case "LPS":
                c=new LPS();
                break;
            case "TSF":
                c=new TSF();
                break;
            case "RISE":
                c=new RISE();
                break;
            case "CIF":
                c=new CIF();
                break;
            case "STSF":
                c=new STSF();
                break;
            case "DrCIF":
                c=new DrCIF();
                break;
            default:
                System.out.println("Unknown interval based classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between array interval and the switch statement ");
                throw new UnsupportedOperationException("Unknown interval based  classifier "+classifier+" should not be able to get here."
                        + "There is a mismatch between array interval and the switch statement ");

        }
        return c;
    }
    /**
     * SHAPELET BASED: Classifiers that use shapelets in some way.
     */
    public static String[] shapelet= {"FastShapelets","LearnShapelets","ShapeletTransformClassifier",
            "ShapeletTreeClassifier","STC","ROCKET","Arsenal","STC-Pruned"};
    public static HashSet<String> shapeletBased=new HashSet<String>( Arrays.asList(shapelet));
    private static Classifier setShapeletBased(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c;
        int fold=exp.foldId;
        switch(classifier) {
            case "LearnShapelets":
                c=new LearnShapelets();
                break;
            case "FastShapelets":
                c=new FastShapelets();
                break;
            case "ShapeletTransformClassifier": case "STC":
                c=new ShapeletTransformClassifier();
                break;
            case "STC-Pruned":
                ShapeletTransformClassifier stc=new ShapeletTransformClassifier();
                stc.setPruneMatchingShapelets(true);
                c=stc;
                break;
            case "ShapeletTreeClassifier":
                c=new ShapeletTree();
                break;
            case "ROCKET":
                c = new ROCKETClassifier();
                break;
            case "Arsenal":
                c = new Arsenal();
                break;
           default:
                System.out.println("Unknown shapelet based classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between array interval and the switch statement ");
                throw new UnsupportedOperationException("Unknown interval based  classifier, should not be able to get here "
                        + "There is a mismatch between array interval and the switch statement ");

        }
        return c;
    }

    /**
     * HYBRIDS: Classifiers that combine two or more of the above approaches
     */
    public static String[] hybrids= {"HiveCoteAlpha", "FlatCote", "HIVE-COTEv1","HIVE-COTEv2", "catch22", "HC-oob", "HC-cv","HC-cv-pf-stc", "HC-cv-stc", "HCV2-cv",
//HC 2 variants
            "HIVE-COTE","HC2","HiveCote",
            "HC-1", "HC-2", "HC-3", "HC-4", "HC-5", "HC-6", "HC-7", "HC-8", "HC-9", "HC-10", "HC-11", "HC-12",
            "HC-13", "HC-14", "HC-15", "HC-16", "HC-17", "HC-18", "HC-19", "HC-20", "HC-21", "HC-22", "HC-23", "HC-24", "HC-25", "HC-26", "HC2-FromFile"
    };
    public static HashSet<String> hybridBased=new HashSet<String>( Arrays.asList(hybrids));
    private static Classifier setHybridBased(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c;
        int fold=exp.foldId;
        switch(classifier) {
            case "FlatCote":
                c=new FlatCote();
                break;
            case "HIVE-COTEv1":
                HIVE_COTE hc=new HIVE_COTE();
                hc.setFillMissingDistsWithOneHotVectors(true);
                hc.setSeed(fold);
                hc.setupHIVE_COTE_1_0();
                c=hc;

                break;
            case "HIVE-COTEv2": case "HiveCote": case "HIVE-COTE":
                hc=new HIVE_COTE();
                hc.setSeed(fold);
                c=hc;
                break;
            case "HC2":
                hc=new HIVE_COTE();
                hc.setSeed(fold);
                hc.enableMultiThreading(4);
                c=hc;
                break;

            case "HC2-FromFile":
                 hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                String[] classifiers={"Arsenal","DrCIF","TDE","STC-D"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-cv-stc":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"Arsenal-cv","DrCIF-cv","TDE-cv","STC-cv"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-cv":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"Arsenal-cv","DrCIF-cv","TDE-cv","STC-cv","PF-cv"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-oob":
                HIVE_COTE hc2=new HIVE_COTE();
                hc2.setBuildIndividualsFromResultsFiles(true);
                hc2.setSeed(fold);
                hc2.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                String[] x2={"Arsenal-oob","DrCIF-oob","TDE-oob","STC-oob","PF-oob"};
                hc2.setClassifiersNamesForFileRead(x2);
                c=hc2;
                break;
            case "catch22":
                c = new Catch22Classifier();
                break;
            case "HC-1":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","Arsenal"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-2":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","STC"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-3":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-4":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"Arsenal","STC"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-5":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"Arsenal","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-6":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"STC","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-7":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","Arsenal","STC"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-8":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","Arsenal","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-9":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","STC","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;

            case "HC-10":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"Arsenal","STC","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;
            case "HC-11":
                hc=new HIVE_COTE();
                hc.setBuildIndividualsFromResultsFiles(true);
                hc.setSeed(fold);
                hc.setDebug(false);
                hc.setResultsFileLocationParameters(exp.resultsWriteLocation,exp.datasetName,fold);
                classifiers=new String[]{"DrCIF","Arsenal","STC","TDE"};
                hc.setClassifiersNamesForFileRead(classifiers);
                c=hc;
                break;




            default:
                System.out.println("Unknown hybrid based classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between array hybrids and the switch statement ");
                throw new UnsupportedOperationException("Unknown hybrid based  classifier, should not be able to get here "
                        + "There is a mismatch between array hybrids and the switch statement ");

        }
        return c;
    }

    /**
     * MULTIVARIATE time series classifiers, all in one list for now
     */
    public static String[] allMultivariate={"Shapelet_I","Shapelet_D","Shapelet_Indep","ED_I","ED_D","DTW_I","DTW_D",
            "DTW_A","HIVE-COTE_I", "HC_I", "CBOSS_I", "RISE_I", "STC_I", "TSF_I","PF_I","TS-CHIEF_I","HC-PF_I",
            "HIVE-COTEn_I","WEASEL-MUSE", "STC-D"};//Not enough to classify yet
    public static HashSet<String> multivariateBased=new HashSet<String>( Arrays.asList(allMultivariate));
    private static Classifier setMultivariate(ExperimentalArguments exp){
        String classifier=exp.estimatorName,resultsPath="",dataset="";
        int fold=exp.foldId;
        Classifier c;
        boolean canLoadFromFile=true;
        if(exp.resultsWriteLocation==null || exp.datasetName==null)
            canLoadFromFile=false;
        else{
            resultsPath=exp.resultsWriteLocation;
            dataset=exp.datasetName;
        }
        switch(classifier) {
            case "Shapelet_I": case "Shapelet_D": case  "Shapelet_Indep"://Multivariate version 1
                c=new MultivariateShapeletTransformClassifier();
                //Default to 1 day max run: could do this better
                ((MultivariateShapeletTransformClassifier)c).setOneDayLimit();
                ((MultivariateShapeletTransformClassifier)c).setSeed(fold);
                ((MultivariateShapeletTransformClassifier)c).setTransformType(classifier);
                break;
            case "ED_I":
                c=new NN_ED_I();
                break;
            case "ED_D":
                c=new NN_ED_D();
                break;
            case "DTW_I":
                c=new NN_DTW_I();
                break;
            case "DTW_D":
                c=new NN_DTW_D();
                break;
            case "DTW_A":
                c=new NN_DTW_A();
                break;
            case "HC_I":
                c=new MultivariateHiveCote(exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "CBOSS_I":
                c=new MultivariateSingleEnsemble("cBOSS", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "RISE_I":
                c=new MultivariateSingleEnsemble("RISE", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "STC_I":
                c=new MultivariateSingleEnsemble("STC", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                ((EnhancedAbstractClassifier)c).setDebug(true);

                break;
            case "TSF_I":
                c=new MultivariateSingleEnsemble("TSF", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "PF_I":
                c=new MultivariateSingleEnsemble("ProximityForest", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "TS-CHIEF_I":
                c=new MultivariateSingleEnsemble("TSCHIEF", exp.resultsWriteLocation, exp.datasetName, exp.foldId);
                break;
            case "HIVE-COTE_I":
                if(canLoadFromFile){
                    String[] cls={"TSF_I","cBOSS_I","RISE_I","STC_I"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HIVE-COTEn_I":
                if(canLoadFromFile){
                    String[] cls={"TSF_I","cBOSS_I","RISE_I","STC_I","TSFn_I","cBOSSn_I","RISEn_I","STCn_I"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-PF_I":
                if(canLoadFromFile){
                    String[] cls={"PF_I","TSF_I","cBOSS_I","RISE_I","STC_I"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "WEASEL-MUSE":
                c=new WEASEL_MUSE();
                break;
            case "STC-D":
                c=new ShapeletTransformClassifier();



                break;
                default:
                System.out.println("Unknown multivariate classifier, should not be able to get here ");
                System.out.println("There is a mismatch between multivariateBased and the switch statement ");
                throw new UnsupportedOperationException("Unknown multivariate classifier, should not be able to get here "
                        + "There is a mismatch between multivariateBased and the switch statement ");
        }
        return c;
    }


    /**
     * STANDARD classifiers such as random forest etc
     */
    public static String[] standard= {
        "XGBoostMultiThreaded","XGBoost","TunedXGBoost","SmallTunedXGBoost","RandF","RotF", "ContractRotF","ERotF","ERotFBag","ERotFOOB","ERotFCV","ERotFTRAIN","PLSNominalClassifier","BayesNet","ED","C45",
            "SVML","SVMQ","TunedSVM","SVMRBF","RidgeCV","MLP","Logistic","CAWPE","NN","NB","BN"};
    public static HashSet<String> standardClassifiers=new HashSet<String>( Arrays.asList(standard));
    private static Classifier setStandardClassifiers(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        int fold=exp.foldId;
        Classifier c;
        switch(classifier) {
            //TIME DOMAIN CLASSIFIERS
            case "XGBoostMultiThreaded":
                c = new TunedXGBoost();
                break;
            case "XGBoost":
                c = new TunedXGBoost();
                ((TunedXGBoost)c).setRunSingleThreaded(true);
                break;
            case "TunedXGBoost":
                c = new TunedXGBoost();
                ((TunedXGBoost)c).setRunSingleThreaded(true);
                ((TunedXGBoost)c).setTuneParameters(true);
                break;
            case "SmallTunedXGBoost":
                c = new TunedXGBoost();
                ((TunedXGBoost)c).setRunSingleThreaded(true);
                ((TunedXGBoost)c).setSmallParaSearchSpace_64paras();
                break;
            case "RandF":
                RandomForest r=new RandomForest();
                r.setNumTrees(500);
                c = r;
                break;
            case "RotF":
                RotationForest rf=new RotationForest();
                rf.setNumIterations(200);
                c = rf;
                break;
            case "ContractRotF":
                ContractRotationForest crf=new ContractRotationForest();
                crf.setMaxNumTrees(200);
                c = crf;
                break;
            case "ERotFBag":
                EnhancedRotationForest erf=new EnhancedRotationForest();
                erf.setBagging(true);
                erf.setMaxNumTrees(200);
                c = erf;
                break;
            case "ERotFOOB":
                erf=new EnhancedRotationForest();
                erf.setBagging(false);
                erf.setTrainEstimateMethod("OOB");
                erf.setMaxNumTrees(200);
                c = erf;
                break;
            case "ERotFCV":
                erf=new EnhancedRotationForest();
                erf.setBagging(false);
                erf.setTrainEstimateMethod("CV");
                erf.setMaxNumTrees(200);
                c = erf;
                break;
            case "ERotF": case "ERotFTRAIN":
                erf=new EnhancedRotationForest();
                erf.setTrainEstimateMethod("TRAIN");
                erf.setMaxNumTrees(200);
                c = erf;
                break;
            case "PLSNominalClassifier":
                c = new PLSNominalClassifier();
                break;
            case "BayesNet":
                c = new BayesNet();
                break;
            case "ED":
                c= KNN.CONFIGS.get(classifier).build();
                break;
            case "C45":
                c=new J48();
                break;
            case "NB":
                c=new NaiveBayes();
                break;
            case "SVML":
                c=new SMO();
                PolyKernel p=new PolyKernel();
                p.setExponent(1);
                ((SMO)c).setKernel(p);
                ((SMO)c).setRandomSeed(fold);
                ((SMO)c).setBuildLogisticModels(true);
                break;
            case "SVMQ":
                c=new SMO();
                PolyKernel poly=new PolyKernel();
                poly.setExponent(2);
                ((SMO)c).setKernel(poly);
                ((SMO)c).setRandomSeed(fold);
                ((SMO)c).setBuildLogisticModels(true);
                break;
            case "SVMRBF":
                c=new SMO();
                RBFKernel rbf=new RBFKernel();
                rbf.setGamma(0.5);
                ((SMO)c).setC(5);
                ((SMO)c).setKernel(rbf);
                ((SMO)c).setRandomSeed(fold);
                ((SMO)c).setBuildLogisticModels(true);
                break;
            case "TunedSVM":
                c=new TunedSVM();
                ((TunedSVM)c).optimiseKernel(true);
                ((TunedSVM)c).setRandomSeed(fold);
                break;
            case "RidgeCV":
                c=new RidgeClassifierCV();
                break;
            case "BN":
                c=new BayesNet();
                break;
            case "MLP":
                c=new MultilayerPerceptron();
                break;
            case "Logistic":
                c= new Logistic();
                break;
            case "CAWPE":
                c=new CAWPE();
                break;
            case "NN":
                kNN k=new kNN(100);
                k.setCrossValidate(true);
                k.normalise(false);
                k.setDistanceFunction(new EuclideanDistance());
                return k;
            default:
                System.out.println("Unknown standard classifier "+classifier+" should not be able to get here ");
                System.out.println("There is a mismatch between otherClassifiers and the switch statement ");
                throw new UnsupportedOperationException("Unknown standard classifier "+classifier+" should not be able to get here "
                        + "There is a mismatch between otherClassifiers and the switch statement ");
        }
        return c;
    }

    /**
     * BESPOKE classifiers for particular set ups. Use if you want some special configuration/pipeline
     * not encapsulated within a single classifier      */
    public static String[] bespoke= {"HIVE-COTE 1.0","HIVE-COTE 2.0","HIVE-COTE","HC-TDE","HC-CIF","HC-WEASEL",
            "HC-BcSBOSS","HC-cSBOSS","TunedHIVE-COTE","HC-S-BOSS",   "HC2-MultiArsenal","HC2-MiniArsenal","HC2-FreshPRINCE","HC2-FreshPRINCE-MultiArsenal"};
    public static HashSet<String> bespokeClassifiers=new HashSet<String>( Arrays.asList(bespoke));
    private static Classifier setBespokeClassifiers(ExperimentalArguments exp){
        String classifier=exp.estimatorName,resultsPath="",dataset="";
        int fold=exp.foldId;
        Classifier c;
        boolean canLoadFromFile=true;
        if(exp.resultsWriteLocation==null || exp.datasetName==null)
            canLoadFromFile=false;
        else{
            resultsPath=exp.resultsWriteLocation;
            dataset=exp.datasetName;
        }
        switch(classifier) {
            case "HIVE-COTE 1.0":
                if(canLoadFromFile){
                    String[] cls={"TSF","RISE","STC","cBOSS"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HIVE-COTE 2.0":
                if(canLoadFromFile){
                    String[] cls={"DrCIF","TDE","Arsenal","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;

            case "HC2-FreshPRINCE":
                if(canLoadFromFile){
                    String[] cls={"DrCIF","TDE","Arsenal","STC","FreshPRINCE"};
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC2-MiniArsenal":
                if(canLoadFromFile){
                    String[] cls={"DrCIF","TDE","MiniArsenal","STC"};
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC2-MultiArsenal":
                if(canLoadFromFile){
                    String[] cls={"DrCIF","TDE","MultiArsenal","STC"};
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC2-FreshPRINCE-MultiArsenal":
                if(canLoadFromFile){
                    String[] cls={"DrCIF","TDE","MultiArsenal","STC","FreshPRINCE"};
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;

            case "HC-TDE":
                if(canLoadFromFile){
                    String[] cls={"TSF","TDE","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-CIF":
                if(canLoadFromFile){
                    String[] cls={"CIF","cBOSS","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-WEASEL":
                if(canLoadFromFile){
                    String[] cls={"TSF","WEASEL","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-S-BOSS":
                if(canLoadFromFile){
                    String[] cls={"TSF","S-BOSS","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-BcSBOSS":
                if(canLoadFromFile){
                    String[] cls={"TSF","BcS-BOSS","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;
            case "HC-cSBOSS":
                if(canLoadFromFile){
                    String[] cls={"TSF","cS-BOSS","RISE","STC"};//RotF for ST
                    c=new HIVE_COTE();
                    ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                    ((HIVE_COTE)c).setSeed(fold);
                    ((HIVE_COTE)c).setBuildIndividualsFromResultsFiles(true);
                    ((HIVE_COTE)c).setResultsFileLocationParameters(resultsPath, dataset, fold);
                    ((HIVE_COTE)c).setClassifiersNamesForFileRead(cls);
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;

            case "HIVE-COTE":
                c=new HIVE_COTE();
                ((HIVE_COTE)c).setFillMissingDistsWithOneHotVectors(true);
                ((HIVE_COTE)c).setSeed(fold);
                break;

            case "TunedHIVE-COTE":
                if(canLoadFromFile){
                    String[] cls=new String[]{"TSF","BOSS","RISE","STC"};//RotF for ST
                    HIVE_COTE hc=new HIVE_COTE();
                    hc.setFillMissingDistsWithOneHotVectors(true);
                    hc.setSeed(fold);
                    hc.setBuildIndividualsFromResultsFiles(true);
                    hc.setResultsFileLocationParameters(resultsPath, dataset, fold);
                    hc.setClassifiersNamesForFileRead(cls);
                    TunedClassifier tuner=new TunedClassifier();
                    tuner.setClassifier(hc);
                    ParameterSpace pc=new ParameterSpace();
                    double[] alphaVals={1,2,3,4,5,6,7,8,9,10};
                    pc.addParameter("A",alphaVals);
                    tuner.setParameterSpace(pc);
                    c=tuner;
                }
                else
                    throw new UnsupportedOperationException("ERROR: currently only loading from file for CAWPE and no results file path has been set. "
                            + "Call setClassifier with an ExperimentalArguments object exp with exp.resultsWriteLocation (contains component classifier results) and exp.datasetName set");
                break;

            default:
                System.out.println("Unknown bespoke classifier, should not be able to get here ");
                System.out.println("There is a mismatch between bespokeClassifiers and the switch statement ");
                throw new UnsupportedOperationException("Unknown bespoke classifier, should not be able to get here "
                        + "There is a mismatch between bespokeClassifiers and the switch statement ");

        }
        return c;
    }

    /**
     * BESPOKE classifiers for particular set ups. Use if you want some special configuration/pipeline
     * not encapsulated within a single classifier      */
    public static String[] earlyClassification= {"TEASER","ECEC","eSTC","P85-DrCIF","P85c3-DrCIF","TEASER-DrCIF",
            "SR1CF1-DrCIF","ECEC-DrCIF"
    };
    public static HashSet<String> earlyClassifiers=new HashSet<String>( Arrays.asList(earlyClassification));
    private static Classifier setEarlyClassifiers(ExperimentalArguments exp){
        String classifier=exp.estimatorName,resultsPath="",dataset="";
        int fold=exp.foldId;
        Classifier c;
        boolean canLoadFromFile=true;
        if(exp.resultsWriteLocation==null || exp.datasetName==null)
            canLoadFromFile=false;
        else{
            resultsPath=exp.resultsWriteLocation;
            dataset=exp.datasetName;
        }

        switch(classifier) {
            case "TEASER":
                c = new EarlyDecisionMakerClassifier(new WEASEL(), new TEASER());
                break;
            case "ECEC":
                c = new EarlyDecisionMakerClassifier(new WEASEL(), new ECEC());
                break;
            case "eSTC":
                c = new ShapeletTransformEarlyClassifier();
                break;

            case "P85-DrCIF":
                c = new EarlyDecisionMakerClassifier(new DrCIF(), new ProbabilityThreshold());
                break;
            case "P85c3-DrCIF":
                c = new EarlyDecisionMakerClassifier(new DrCIF(), new ProbabilityThreshold());
                ((ProbabilityThreshold) ((EarlyDecisionMakerClassifier) c).getDecisionMaker()).setConsecutivePredictions(3);
                break;
            case "TEASER-DrCIF":
                c = new EarlyDecisionMakerClassifier(new DrCIF(), new TEASER());
                break;
            case "SR1CF1-DrCIF":
                c = new EarlyDecisionMakerClassifier(new DrCIF(), new SR1CF1());
                break;
            case "ECEC-DrCIF":
                c = new EarlyDecisionMakerClassifier(new DrCIF(), new ECEC());
                break;

            default:
                System.out.println("Unknown bespoke classifier, should not be able to get here ");
                System.out.println("There is a mismatch between bespokeClassifiers and the switch statement ");
                throw new UnsupportedOperationException("Unknown bespoke classifier, should not be able to get here "
                        + "There is a mismatch between bespokeClassifiers and the switch statement ");

        }
        return c;
    }

    /**
     *
     * setClassifier, which takes the experimental
     * arguments themselves and therefore the classifiers can take from them whatever they
     * need, e.g the dataset name, the fold id, separate checkpoint paths, etc.
     *
     * To take this idea further, to be honest each of the TSC-specific classifiers
     * could/should have a constructor and/or factory that builds the classifier
     * from the experimental args.
     *
     * previous usage was setClassifier(String classifier name, int fold).
     * this can be reproduced with setClassifierClassic below.
     *
     */
    public static Classifier setClassifier(ExperimentalArguments exp){
        String classifier=exp.estimatorName;
        Classifier c = null;
        if(distanceBased.contains(classifier))
            c=setDistanceBased(exp);
        else if(dictionaryBased.contains(classifier))
            c=setDictionaryBased(exp);
        else if(intervalBased.contains(classifier))
            c=setIntervalBased(exp);
        else if(shapeletBased.contains(classifier))
            c=setShapeletBased(exp);
        else if(hybridBased.contains(classifier))
            c=setHybridBased(exp);
        else if(multivariateBased.contains(classifier))
            c=setMultivariate(exp);
        else if(standardClassifiers.contains(classifier))
            c=setStandardClassifiers(exp);
        else if(bespokeClassifiers.contains(classifier))
            c=setBespokeClassifiers(exp);
        else if(earlyClassifiers.contains(classifier))
            c=setEarlyClassifiers(exp);
        else{
            System.out.println("Unknown classifier "+classifier+" it is not in any of the sublists ");
            throw new UnsupportedOperationException("Unknown classifier "+classifier+" it is not in any of the sublists on ClassifierLists ");
        }
        if (c instanceof EnhancedAbstractClassifier) {
            ((EnhancedAbstractClassifier) c).setSeed(exp.foldId);
            ((EnhancedAbstractClassifier) c).setDebug(exp.debug);
        } 
        else if (c instanceof Randomizable) {
            //normal weka classifiers that aren't EnhancedAbstractClassifiers
            //EAC's setSeed sets up a random object internally too. 
            ((Randomizable)c).setSeed(exp.foldId);
        }
        return c;
    }


    /**
     * This method redproduces the old usage exactly as it was in old experiments.java.
     * If you try build any classifier that uses any experimental info other than
     * exp.classifierName or exp.foldID, an exception will be thrown.
     * In particular, any classifier that needs access to the results from others
     * e.g. CAWPEFROMFILE, will throw an UnsupportedOperationException if you try use it like this.
     *      * @param classifier
     * @param fold
     * @return
     */
    public static Classifier setClassifierClassic(String classifier, int fold){
        ExperimentalArguments exp=new ExperimentalArguments();
        exp.estimatorName =classifier;
        exp.foldId=fold;
        return setClassifier(exp);
    }






    public static void main(String[] args) throws Exception {
        System.out.println("Testing set classifier by running through the list in ClassifierLists.allUnivariate and " +
                "ClassifierLists.allMultivariate");

        for(String str:allUnivariate){
            System.out.println("Initialising "+str);
            Classifier c= setClassifierClassic(str,0);
            System.out.println("Returned classifier "+c.getClass().getSimpleName());
        }
        for(String str:allMultivariate){
            System.out.println("Initialising "+str);
            Classifier c= setClassifierClassic(str,0);
            System.out.println("Returned classifier "+c.getClass().getSimpleName());
        }
        for(String str:standard){
            System.out.println("Initialising "+str);
            Classifier c= setClassifierClassic(str,0);
            System.out.println("Returned classifier "+c.getClass().getSimpleName());
        }
        for(String str:bespoke){
            System.out.println("Initialising "+str);
            Classifier c= setClassifierClassic(str,0);
            System.out.println("Returned classifier "+c.getClass().getSimpleName());
        }


    }
}
