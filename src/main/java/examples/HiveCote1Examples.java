/*
 * This file is part of the UEA Time Series Machine Learning (TSML) toolbox.
 *
 * The UEA TSML toolbox is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published 
 * by the Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version.
 *
 * The UEA TSML toolbox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the UEA TSML toolbox. If not, see <https://www.gnu.org/licenses/>.
 */

package examples;

import experiments.ExperimentalArguments;
import experiments.ClassifierExperiments;
import experiments.data.DatasetLoading;
import tsml.classifiers.EnhancedAbstractClassifier;
import tsml.classifiers.dictionary_based.cBOSS;
import tsml.classifiers.interval_based.RISE;
import tsml.classifiers.hybrids.HIVE_COTE;
import tsml.classifiers.interval_based.TSF;
import weka.core.Instance;
import weka.core.Instances;

import java.util.concurrent.TimeUnit;

/**
 * This class demonstrates how to use the HIVE-COTE classifier
 * HIVE-COTE version 0.1: published here
 * HIVE-COTE version 1.0: published here
 *
 */
public class HiveCote1Examples {

    public static void basicUsage() throws Exception {
        System.out.println(" Basic Usage: load a dataset, build a default config classifier, make some predictions:");
        HIVE_COTE hc = new HIVE_COTE();
        System.out.println(" Current default for HC is to use version 1.0: RISE, TSF, cBOSS and STC");
//        hc.setupHIVE_COTE_1_0(); //called in default constructor
//        hc.setupHIVE_COTE_0_1(); // approximation of original version (STC has changed
        Instances[] trainTest = DatasetLoading.sampleItalyPowerDemand(0); //Loads the default italy power demans
        hc.setDebug(true);//Verbose version
        hc.buildClassifier(trainTest[0]);
        int correct=0;
        for(Instance ins: trainTest[1]){
            double c=hc.classifyInstance(ins);
            if(c==ins.classValue())
                correct++;
        }
        System.out.println(" Number correct = "+correct+ " accuracy = "+correct/(double)trainTest[1].numInstances());
    }

    public static void usageWithExperimentsClass() throws Exception {
        System.out.println(" the class src/main/java/experiments/ClassifierExperiments.java standardises the output of classifiers" +
                " to facilitate easy post processing" );
        System.out.println(" ClassifierExperiments.java allows for configuration  through command line arguments. Extensive " +
                "documentation on the possible configurations is at the top of the class" );
        System.out.println(" ClassifierExperiments.java creates a classifier based on switches in src/main/java/experiments/ClassifierLists.java" );
        System.out.println(" This method shows how to use it through string passing via the ExperimentsArguments formatting class" );
        System.out.println(" Command line uses the same string format" );
        System.out.println(" Experiment is configurable for contracting and checkpointing" );
        System.out.println(" However, you cannot yet configure the classifier directly. The purpose of this class is to " +
                "run large scale comaprative studies" );
        System.out.println(" You can, if you wish, add a bespoke option to ClassifierLists.java. Use setBespokeClassifiers" +
                "and remember to add your option to the string array bespoke" );
//Local run without args, mainly for debugging
        String[] settings=new String[9];
//Location of data set
        settings[0]="-dp=src/main/java/experiments/data/tsc/";//Where to get data
        settings[1]="-rp=Temp/";//Where to write results
        settings[2]="-gtf=false"; //Whether to generate train files or not
        settings[3]="-cn=HIVE-COTE"; //Classifier name: See ClassifierLists for valid options
        settings[4]="-dn=Chinatown"; //Problem file, added below
        settings[5]="-f=1";//Fold number, added below (fold number 1 is stored as testFold0.csv, its a cluster thing)
        settings[6]="--force=true";//Overwrites existing results if true, otherwise set to false
        settings[7]="-ctr=0";//No time contract
        settings[8]="-cp=0";//No checkpointing
        ClassifierExperiments.debug=true;
        System.out.println("Manually set args:");
        for (String str : settings)
            System.out.println("\t"+str);
        System.out.println("");
        ExperimentalArguments expSettings = new ExperimentalArguments(settings);
        ClassifierExperiments.setupAndRunExperiment(expSettings);
        System.out.println(" The output will be in Temp/HIVE-COTE/ChinaTown/Predictions/testFold9.csv");
        System.out.println(" The format of this file is explained in ");

    }


    public static void buildingFromComponents() throws Exception {
        System.out.println("We always build HIVE-COTE from constituent components " );
        System.out.printf(" Components results must be in the standard format, generated by ClassifierExperiments.java");

        String problem="Chinatown";
        //Local run without args, mainly for debugging
        String[] settings=new String[6];
//Location of data set
        settings[0]="-dp=src/main/java/experiments/data/tsc/";//Where to get data
        settings[1]="-rp=C:/Temp";//Where to write results
        settings[2]="-gtf=true"; //HIVE-COTE requires train files
        settings[3]="-cn="; //Classifier name: See ClassifierLists for valid options
        settings[4]="-dn="+problem; //Problem file,
        settings[5]="-f=1";//Fold number, added below (fold number 1 is stored as testFold0.csv, its a cluster thing)
        ClassifierExperiments.debug=true;
        System.out.println("Manually set args:");
        for (String str : settings)
            System.out.println("\t"+str);
        System.out.println("");
        String[] components={"TSF","RISE","cBOSS","STC"};
        for(String s:components){
            System.out.println("Building "+s);
            settings[3]="-cn="+s; //Classifier name: See ClassifierLists for valid options
            ExperimentalArguments expSettings = new ExperimentalArguments(settings);
            ClassifierExperiments.setupAndRunExperiment(expSettings);
            System.out.println(s+" Finished");
        }
        System.out.println(" Components finished. You can run the load from file using experiments with argument " +
                "HIVE_COTE1.0 ");
        settings[2]="-gtf=false"; //Dont need HC train file
        settings[3]="-cn=HIVE-COTE1.0"; //Classifier name: See ClassifierLists for valid options
        ExperimentalArguments expSettings = new ExperimentalArguments(settings);
        ClassifierExperiments.setupAndRunExperiment(expSettings);
        System.out.println("HIVE-COTE Finished. Results will be in C:/Temp/HIVE-COTE1.0/Predictions/Chinatown/");
        System.out.println(" or just run it yourself");
        HIVE_COTE hc=new HIVE_COTE();
        hc.setBuildIndividualsFromResultsFiles(true);
        hc.setResultsFileLocationParameters("C:/Temp/", problem, 0);
        hc.setClassifiersNamesForFileRead(components);
        Instances train = DatasetLoading.loadData("src/main/java/experiments/data/tsc/Chinatown/Chinatown_TRAIN");
        Instances test = DatasetLoading.loadData("src/main/java/experiments/data/tsc/Chinatown/Chinatown_TEST");
        hc.setDebug(true);//Verbose version
        hc.buildClassifier(train);
        int correct=0;
        for(Instance ins: test){
            double c=hc.classifyInstance(ins);
            if(c==ins.classValue())
                correct++;
        }
        System.out.println(" Number correct = "+correct+ " accuracy = "+correct/(double)test.numInstances());


    }


    public static void setClassifiersAndThread() throws Exception {
        System.out.println(" HIVE COTE is configurable in many ways");
        HIVE_COTE hc = new HIVE_COTE();
        System.out.println(" Current default for HC is to use version 1.0: RISE, TSF, cBOSS and STC");
        System.out.printf("Suppose we want different classifiers\n");
        EnhancedAbstractClassifier[] c=new EnhancedAbstractClassifier[2];
        c[0]=new RISE();
        c[1]=new TSF();
        String[] names={"RISE","TSF"};
        hc.setClassifiers(c,names,null);
        String[] n=hc.getClassifierNames();
        for(String s:n)
            System.out.println(" Classifier "+s);

        System.out.println(" We can make it threaded so each component runs in its own thread\n");
        System.out.println(" Threading demonstrated by interleaved printouts\n");
        hc.enableMultiThreading(2);
        Instances[] trainTest = DatasetLoading.sampleItalyPowerDemand(0); //Loads the default italy power demans
        hc.setDebug(true);
        hc.buildClassifier(trainTest[0]);
        int correct=0;
        for(Instance ins: trainTest[1]){
            double cls=hc.classifyInstance(ins);
            if(cls==ins.classValue())
                correct++;
        }
        System.out.println(" Number correct = "+correct+ " accuracy = "+correct/(double)trainTest[1].numInstances());
        System.out.println(" HIVE COTE is contractable, if its components are contractable.");
        System.out.println(" To set a contract (max run time) use any of the Contractable methods\n");
        hc.enableMultiThreading(1);

        hc.setTrainTimeLimit(10, TimeUnit.SECONDS);
        long t =System.nanoTime();
        hc.buildClassifier(trainTest[0]);
        t =System.nanoTime()-t;
        System.out.println("\t\t Time elapsed = "+t/1000000000+" seconds");

    }


    public static void contract() throws Exception {
        System.out.println(" HIVE COTE is contractable and checkpointable");
        System.out.println(" these can be set through ClassifierExperiments or directly");
        HIVE_COTE hc = new HIVE_COTE();
        EnhancedAbstractClassifier[] c=new EnhancedAbstractClassifier[3];
        c[0]=new TSF();
        c[1]=new RISE();
        c[2]=new cBOSS();
//        c[0]=new ShapeletTransformClassifier();
        String[] names={"TSF","RISE","cBOSS"};//"STC"};//
        hc.setClassifiers(c,names,null);
        String[] n=hc.getClassifierNames();
        for(String s:n)
            System.out.println(" Classifier "+s);
        Instances train = DatasetLoading.loadData("src/main/java/experiments/data/tsc/Beef/Beef_TRAIN");
        Instances test = DatasetLoading.loadData("src/main/java/experiments/data/tsc/Beef/Beef_TEST");

        hc.setDebug(true);
        System.out.println(" HIVE COTE is contractable only if its components are contractable.");
        System.out.println(" To set a contract (max run time) use any of the Contractable methods\n");
//Ways of setting the contract time
//Minute, hour or day limit
        hc.setMinuteLimit(1);
        hc.setHourLimit(2);
        hc.setDayLimit(1);
//Specify units
        hc.setTrainTimeLimit(30, TimeUnit.SECONDS);
        hc.setTrainTimeLimit(1, TimeUnit.MINUTES);
//Or just give it in nanoseconds
        hc.setTrainTimeLimit(10000000000L);
        long t =System.nanoTime();
        hc.buildClassifier(train);
        t =System.nanoTime()-t;
        System.out.println("\t\t Time elapsed = "+t/1000000000+" seconds");

    }


    public static void main(String[] args) throws Exception {
        HIVE_COTE hc = new HIVE_COTE();
        System.out.println(" HIVE COTE classifier, location "+hc.getClass().getName());
//        basicUsage();
//        usageWithExperimentsClass();
//        buildingFromComponents();
 //       setClassifiersAndThread();
        contract();
    }
}
