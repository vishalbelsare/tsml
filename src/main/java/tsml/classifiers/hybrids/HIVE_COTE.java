/*
 * Copyright (C) 2019 xmw13bzu
 *
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

package tsml.classifiers.hybrids;

import evaluation.evaluators.CrossValidationEvaluator;
import evaluation.tuning.ParameterSpace;
import machine_learning.classifiers.ensembles.AbstractEnsemble;
import machine_learning.classifiers.ensembles.voting.MajorityConfidence;
import machine_learning.classifiers.ensembles.weightings.TrainAcc;
import tsml.classifiers.EnhancedAbstractClassifier;
import tsml.classifiers.TrainTimeContractable;
import tsml.classifiers.Tuneable;
import tsml.classifiers.dictionary_based.BOSS;
import tsml.classifiers.dictionary_based.TDE;
import tsml.classifiers.dictionary_based.cBOSS;
import tsml.classifiers.distance_based.ElasticEnsemble;
import tsml.classifiers.interval_based.DrCIF;
import tsml.classifiers.interval_based.RISE;
import tsml.classifiers.interval_based.TSF;
import tsml.classifiers.kernel_based.Arsenal;
import tsml.classifiers.shapelet_based.ShapeletTransformClassifier;
import tsml.data_containers.TimeSeriesInstances;
import tsml.data_containers.utilities.Converter;
import tsml.transformers.Resizer;
import utilities.ClassifierTools;
import weka.classifiers.Classifier;
import weka.core.*;

import java.util.concurrent.TimeUnit;

/**
 *
 * @author James Large (james.large@uea.ac.uk), Tony Bagnall
 * @maintainer Tony Bagnall
 *
 * This classifier is the latest version of the Hierarchical Vote Ensemble Collective of Transformation-based
 * Ensembles (HIVE-COTE).
 *
 * The original classifier, now called HiveCote 0.1, described in [1] has  been moved to legacy_cote.
 * This new one
 *
 * 1. Threadable
 * 2. Contractable
 * 3. Tuneable
 *
 * Version 1.0:
 */
public class HIVE_COTE extends AbstractEnsemble implements TechnicalInformationHandler, TrainTimeContractable, Tuneable {

    //TrainTimeContractable
    protected boolean trainTimeContract = false;
    protected long trainContractTimeNanos = TimeUnit.DAYS.toNanos(7); // if contracting with no time limit given, default to 7 days.
    protected TimeUnit contractTrainTimeUnit = TimeUnit.NANOSECONDS;

    /**
     * Utility if we want to be conservative while contracting with the overhead 
     * of the ensemble and any variance with the base classifiers' abilities to adhere 
     * to the contract. Give the base classifiers a (very large not not full) proportion
     * of the contract time given, and allow some extra time for the ensemble overhead,
     * potential threading overhead, etc
     */
    protected final double BASE_CLASSIFIER_CONTRACT_PROP = 0.99; //if e.g 1 day contract, 864 seconds grace time
    protected double alpha=4.0; // Weighting parameter for voting method

    private int defaultSettings = 2;
    private Resizer resizer;

    @Override
    public TechnicalInformation getTechnicalInformation() {
        TechnicalInformation 	result;
        result = new TechnicalInformation(TechnicalInformation.Type.ARTICLE);
        result.setValue(TechnicalInformation.Field.AUTHOR, "J. Lines, S. Taylor and A. Bagnall");
        result.setValue(TechnicalInformation.Field.TITLE, "Time Series Classification with HIVE-COTE: The Hierarchical Vote Collective of Transformation-Based Ensembles");
        result.setValue(TechnicalInformation.Field.JOURNAL, "ACM Transactions on Knowledge Discovery from Data");
        result.setValue(TechnicalInformation.Field.VOLUME, "12");
        result.setValue(TechnicalInformation.Field.NUMBER, "5");
        
        result.setValue(TechnicalInformation.Field.PAGES, "52");
        result.setValue(TechnicalInformation.Field.YEAR, "2018");
        return result;
    }    

    public HIVE_COTE() { 
        super();
    }
    @Override
    public void setupDefaultEnsembleSettings() {
        defaultSettings = 2;
    }


    public void setupHIVE_COTE_0_1() {
        defaultSettings = 0;
    }
    private void buildHC0() {
        //copied over/adapted from HiveCote.setDefaultEnsembles()
        //TODO jay/tony review
        this.ensembleName = "HIVE-COTE 0.1";

        if (this.weightingScheme == null)
            this.weightingScheme = new TrainAcc(alpha);
        if (this.votingScheme == null)
            this.votingScheme = new MajorityConfidence();

        if (this.trainEstimator == null) {
            CrossValidationEvaluator cv = new CrossValidationEvaluator(seed, false, false, false, false);
            cv.setNumFolds(10);
            this.trainEstimator = cv;
        }

        if (modules == null) {
            Classifier[] classifiers = new Classifier[5];
            String[] classifierNames = new String[5];

            EnhancedAbstractClassifier ee = new ElasticEnsemble();
            ee.setEstimateOwnPerformance(true);
            classifiers[0] = ee;
            classifierNames[0] = "EE";

    //        CAWPE st_classifier = new CAWPE();
    //        DefaultShapeletTransformPlaceholder st_transform= new DefaultShapeletTransformPlaceholder();
    //        st_classifier.setTransform(st_transform);
            ShapeletTransformClassifier stc = new ShapeletTransformClassifier();
            if (trainTimeContract)
                stc.setTrainTimeLimit(contractTrainTimeUnit, trainContractTimeNanos);
            stc.setEstimateOwnPerformance(true);
            classifiers[1] = stc;
            classifierNames[1] = "STC";

            classifiers[2] = new RISE();
            classifierNames[2] = "RISE";

            BOSS boss = new BOSS();
            boss.setEstimateOwnPerformance(true);
            classifiers[3] = boss;
            classifierNames[3] = "BOSS";

            TSF tsf = new TSF();
            tsf.setEstimateOwnPerformance(true);
            classifiers[4] = tsf;
            classifierNames[4] = "TSF";

            try {
                setClassifiers(classifiers, classifierNames, null);
            } catch (Exception e) {
                System.out.println("Exception thrown when setting up DEFAULT settings of " + this.getClass().getSimpleName() + ". Should "
                        + "be fixed before continuing");
                System.exit(1);
            }
        }

        for (EnsembleModule module : modules)
            if(module.getClassifier() instanceof Randomizable)
                ((Randomizable)module.getClassifier()).setSeed(seed);

        if(trainTimeContract)
            setTrainTimeLimit(contractTrainTimeUnit, trainContractTimeNanos);
    }

    public void setupHIVE_COTE_1_0() {
        defaultSettings = 1;
    }
    private void buildHC1() {
        this.ensembleName = "HIVE-COTE 1.0";

        if (this.weightingScheme == null)
            this.weightingScheme = new TrainAcc(alpha);
        if (this.votingScheme == null)
            this.votingScheme = new MajorityConfidence();

        if (this.trainEstimator == null) {
            CrossValidationEvaluator cv = new CrossValidationEvaluator(seed, false, false, false, false);
            cv.setNumFolds(10);
            this.trainEstimator = cv;
        }

        if (modules == null) {
            Classifier[] classifiers = new Classifier[4];
            String[] classifierNames = new String[4];

            ShapeletTransformClassifier stc = new ShapeletTransformClassifier();
            stc.setEstimateOwnPerformance(true);
            classifiers[0] = stc;
            classifierNames[0] = "STC";

            classifiers[1] = new RISE();
            classifierNames[1] = "RISE";

            cBOSS boss = new cBOSS();
            boss.setEstimateOwnPerformance(true);
            classifiers[2] = boss;
            classifierNames[2] = "cBOSS";

            TSF tsf = new TSF();
            classifiers[3] = tsf;
            classifierNames[3] = "TSF";
            tsf.setEstimateOwnPerformance(true);

            try {
                setClassifiers(classifiers, classifierNames, null);
            } catch (Exception e) {
                System.out.println("Exception thrown when setting up DEFAULT settings of " + this.getClass().getSimpleName() + ". Should "
                        + "be fixed before continuing");
                System.exit(1);
            }
        }

        for (EnsembleModule module : modules)
            if(module.getClassifier() instanceof Randomizable)
                ((Randomizable)module.getClassifier()).setSeed(seed);
        
        if(trainTimeContract)
            setTrainTimeLimit(contractTrainTimeUnit, trainContractTimeNanos);
    }



    public void setupHIVE_COTE_2_0() {
        defaultSettings = 2;
    }
    private void buildHC2() {
        this.ensembleName = "HIVE-COTE 2.0";

        if (this.weightingScheme == null)
            this.weightingScheme = new TrainAcc(alpha);
        if (this.votingScheme == null)
            this.votingScheme = new MajorityConfidence();

        if (this.trainEstimator == null) {
            CrossValidationEvaluator cv = new CrossValidationEvaluator(seed, false, false, false, false);
            cv.setNumFolds(10);
            this.trainEstimator = cv;
        }

        if (modules == null) {
            ShapeletTransformClassifier stc = new ShapeletTransformClassifier();
            DrCIF cif = new DrCIF();
            Arsenal afc = new Arsenal();
            TDE tde = new TDE();
            String[] classifierNames = new String[4];
            classifierNames[0] = "STC";
            classifierNames[1] = "DrCIF";
            classifierNames[2] = "Arsenal";
            classifierNames[3] = "TDE";
            EnhancedAbstractClassifier[] classifiers = new EnhancedAbstractClassifier[4];
            classifiers[0] = stc;
            classifiers[1] = cif;
            classifiers[2] = afc;
            classifiers[3] = tde;
            for (EnhancedAbstractClassifier cls : classifiers) {
                cls.setEstimateOwnPerformance(true);
                cls.setTrainEstimateMethod(TrainEstimateMethod.OOB);
            }
            try {
                setClassifiers(classifiers, classifierNames, null);
            } catch (Exception e) {
                System.out.println("Exception thrown when setting up DEFAULT settings of " + this.getClass().getSimpleName() + ". Should "
                        + "be fixed before continuing");
                System.exit(1);
            }
        }

        for (EnsembleModule module : modules)
            if(module.getClassifier() instanceof Randomizable)
                ((Randomizable)module.getClassifier()).setSeed(seed);

        if(trainTimeContract)
            setTrainTimeLimit(contractTrainTimeUnit, trainContractTimeNanos);
    }

    @Override
    public void buildClassifier(TimeSeriesInstances data) throws Exception {
        if (defaultSettings == 0){
            buildHC0();
        }
        else if (defaultSettings == 1){
            buildHC1();
        }
        else if (defaultSettings == 2){
            buildHC2();
        }

        getCapabilities().testWithFail(Converter.toArff(data));
        if (!data.isEqualLength()) {
            // pad with 0s
            resizer = new Resizer(new Resizer.MaxResizeMetric(), new Resizer.FlatPadMetric(0));
            TimeSeriesInstances padded = resizer.fitTransform(data);
            data = padded;
        }

        if(debug) {
            printDebug(" Building HIVE-COTE with components: ");
            for (EnsembleModule module : modules){
                if (module.getClassifier() instanceof EnhancedAbstractClassifier)
                    ((EnhancedAbstractClassifier) module.getClassifier()).setDebug(debug);
                printDebug(module.getModuleName()+" ");
            }
            printDebug(" \n ");
        }

        if (trainTimeContract){
            printLineDebug(" In build of HC2: contract time = "+trainContractTimeNanos/1000000000/60/60+" hours ");
            setupContracting();
        }

        super.buildClassifier(Converter.toArff(data));
        trainResults.setParas(getParameters());
        printLineDebug("*************** Finished HIVE-COTE Build with train time " +
                (trainResults.getBuildTime()/1000000000/60/60.0) + " hours, Train+Estimate time = "+(trainResults.getBuildPlusEstimateTime()/1000000000/60/60.0)+" hours ***************");

    }


    @Override
    public void buildClassifier(Instances data) throws Exception {
        if (defaultSettings == 0){
            buildHC0();
        }
        else if (defaultSettings == 1){
            buildHC1();
        }
        else if (defaultSettings == 2){
            buildHC2();
        }

        if(debug) {
            printDebug(" Building HIVE-COTE with components: ");
            for (EnsembleModule module : modules){
                if (module.getClassifier() instanceof EnhancedAbstractClassifier)
                    ((EnhancedAbstractClassifier) module.getClassifier()).setDebug(debug);
                printDebug(module.getModuleName()+" ");
            }
            printDebug(" \n ");
        }

        if (trainTimeContract){
            printLineDebug(" In build of HC2: contract time = "+trainContractTimeNanos/1000000000/60/60+" hours ");
            setupContracting();
        }

        super.buildClassifier(data);
        trainResults.setParas(getParameters());
        printLineDebug("*************** Finished HIVE-COTE Build with train time " +
                (trainResults.getBuildTime()/1000000000/60/60.0) + " hours, Train+Estimate time = "+(trainResults.getBuildPlusEstimateTime()/1000000000/60/60.0)+" hours ***************");

    }
    /**
     * Returns default capabilities of the classifier. These are that the
     * data must be numeric, with no missing and a nominal class
     * @return the capabilities of this classifier
     */
    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();
        // attributes must be numeric
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        // Can only handle discrete class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);
        // instances
        result.setMinimumNumberInstances(1);
        if(readIndividualsResults)//Can handle all data sets
            result.enableAll();
        return result;
    }


    /**
     * Will split time given evenly among the contractable base classifiers. 
     * 
     * This is currently very naive, and likely innaccurate. Consider these TODOs
     * 
     *  1) If there are any non-contractable base classifiers, these are ignored in 
     *      the contract setting. The full time is allocated among the contractable 
     *      base classifiers, instead of trying to do any wonky guessing of how long the 
     *      non-contractable ones might take
     *  2) Currently, generating accuracy estimates is not considered in the contract.
     *      If there are any non-TrainAccuracyEstimating classifiers, the estimation procedure (e.g.
     *      a 10fold cv) will very likely overshoot the contract, since the classifier would be
     *      trying to keep to contract on each fold and the full build individually, not in total. 
     *      This is an active research question moreso than an implementation question
     *  3) The contract currently does not consider whether the ensemble is being threaded,
     *      i.e. even if it can run the building of two or more classifiers in parallel, 
     *      this will still naively set the contract per classifier as amount/numClassifiers
     */
    /**
     * Overriding TrainTimeContract methods
     * @param amount of time in nanos
     */
    @Override //TrainTimeContractable
    public void setTrainTimeLimit(long amount) {
        trainTimeContract = true;
        trainContractTimeNanos = amount;
        contractTrainTimeUnit = TimeUnit.NANOSECONDS;
    }
    @Override
    public boolean withinTrainContract(long start) {
        return start<trainContractTimeNanos;
    }

    /**
     * Sets up the ensemble for contracting, to be called at the start of build classifier,
     * i.e. when parameters can no longer be changed.
     */
    protected void setupContracting() {
        //splits the ensemble contract time between this many classifiers
        int numContractableClassifiers = 0; 
        
        //in future, the number of classifiers we need to separately eval and custom-contract for
        int numNonTrainEstimatingClassifiers = 0; 
        printLineDebug(" Setting up contracting. Number of modules  = "+modules.length);
        for (EnsembleModule module : modules) {
            if(module.isTrainTimeContractable())
                numContractableClassifiers++;
            else 
                System.out.println("WARNING: trying to contract " + ensembleName + ", but base classifier " + module.getModuleName() + " is not contractable, "
                        + "and is therefore not considered in the contract. The ensemble as a whole will very likely not meet the contract.");
            
            if(!module.isAbleToEstimateOwnPerformance()) {
                numNonTrainEstimatingClassifiers++;
                System.out.println("WARNING: trying to contract " + ensembleName + ", but base classifier " + module.getModuleName() + " does not estimate its own accuracy. "
                        + "Performing a separate evaluation on the train set currently is not considered in the contract, and therefore the ensemble as a whole will very "
                        + "likely not meet the contract.");
            }
        }

        //force nanos in setting base classifier contracts in case e.g. 1 hour was passed, 1/5 = 0...
        TimeUnit highFidelityUnit = TimeUnit.NANOSECONDS;
        long conservativeBaseClassifierContract = (long) (BASE_CLASSIFIER_CONTRACT_PROP * highFidelityUnit.convert(trainContractTimeNanos, contractTrainTimeUnit));
        long highFidelityTimePerClassifier;
        if(multiThread)
            highFidelityTimePerClassifier= (conservativeBaseClassifierContract);
        else
            highFidelityTimePerClassifier= (conservativeBaseClassifierContract) / numContractableClassifiers;
        printLineDebug(" Setting up contract\nTotal Contract = "+(trainContractTimeNanos/1000000000/60/60)+" hours");
        printLineDebug(" Per Classifier = "+highFidelityTimePerClassifier+" Nanos");
        for (EnsembleModule module : modules)
            if(module.isTrainTimeContractable())
                ((TrainTimeContractable) module.getClassifier()).setTrainTimeLimit(highFidelityUnit, highFidelityTimePerClassifier);
    }

    public void setAlpha(double alpha){
        this.alpha = alpha;
        this.weightingScheme = new TrainAcc(this.alpha);
    }
    
    @Override   //EnhancedAbstractClassifier
    public void setSeed(int seed) { 
        super.setSeed(seed);
    }

    @Override //AbstractClassifier
    public void setOptions(String[] options) throws Exception {
//        System.out.print("TSF para sets ");
//        for (String str:options)
//             System.out.print(","+str);
//        System.out.print("\n");
        String a = Utils.getOption('A', options);
        alpha=Double.parseDouble(a);
        this.weightingScheme = new TrainAcc(alpha);

    }
    /**
     *TUNED TSF Classifiers. Method for interface Tuneable
     * Valid options are: <p/>
     * <pre> -T Number of trees.</pre>
     * <pre> -I Number of intervals to fit.</pre>
     *
     *
     * @return ParameterSpace object
     */
    @Override //Tuneable
    public ParameterSpace getDefaultParameterSearchSpace(){
        ParameterSpace ps=new ParameterSpace();
        String[] alphaRange={"1.0","2.0","3.0","4.0","5.0","6.0","7.0","8.0","9.0","10.0"};
        ps.addParameter("A", alphaRange);

        return ps;
    }

    @Override
    public String getParameters() {
        String str="WeightingScheme,"+weightingScheme+","+"VotingScheme,"+votingScheme+",alpha,"+alpha+
                ",seedClassifier,"+seedClassifier+",seed,"+seed;
        if (trainTimeContract) str += ",contractTime(hrs),"+trainContractTimeNanos/1000000000/60/60.0;

        for (EnsembleModule module : modules)
            str+=","+module.getModuleName()+","+module.posteriorWeights[0];

        //This gets really long and it only really used for debugging
        if (readIndividualsResults)
            for (EnsembleModule module : modules)
                str += module.getParameters() + ",,";

        return str;

    }


    public static void main(String[] args) throws Exception {
        System.out.println(ClassifierTools.testUtils_getIPDAcc(new HIVE_COTE()));
    }
}
