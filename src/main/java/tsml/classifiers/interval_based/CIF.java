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

package tsml.classifiers.interval_based;

import evaluation.evaluators.CrossValidationEvaluator;
import evaluation.storage.ClassifierResults;
import evaluation.tuning.ParameterSpace;
import experiments.data.DatasetLoading;
import fileIO.OutFile;
import machine_learning.classifiers.ContinuousIntervalTree;
import machine_learning.classifiers.ContinuousIntervalTree.Interval;
import tsml.classifiers.*;
import tsml.data_containers.TSCapabilities;
import tsml.data_containers.TimeSeriesInstance;
import tsml.data_containers.TimeSeriesInstances;
import tsml.data_containers.utilities.Converter;
import tsml.transformers.Catch22;
import utilities.ClassifierTools;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Function;

import static tsml.classifiers.interval_based.DrCIF.*;
import static utilities.Utilities.argMax;

/**
 * Implementation of the catch22 Interval Forest (CIF) algorithm
 *
 * @author Matthew Middlehurst
 **/
public class CIF extends EnhancedAbstractClassifier implements TechnicalInformationHandler, TrainTimeContractable,
        Checkpointable, Tuneable, MultiThreadable, Visualisable, Interpretable {

    /**
     * Paper defining CIF.
     *
     * @return TechnicalInformation for CIF
     */
    @Override //TechnicalInformationHandler
    public TechnicalInformation getTechnicalInformation() {
        TechnicalInformation result;
        result = new TechnicalInformation(TechnicalInformation.Type.ARTICLE);
        result.setValue(TechnicalInformation.Field.AUTHOR, "M. Middlehurst, J. Large and A. Bagnall");
        result.setValue(TechnicalInformation.Field.TITLE, "The Canonical Interval Forest (CIF) Classifier for " +
                "Time Series Classifciation");
        result.setValue(TechnicalInformation.Field.JOURNAL, "IEEE International Conference on Big Data");
        result.setValue(TechnicalInformation.Field.YEAR, "2020");
        return result;
    }

    /**
     * Primary parameters potentially tunable
     */
    private int numClassifiers = 500;

    /**
     * Amount of attributes to be subsampled and related data storage.
     */
    private int attSubsampleSize = 8;
    private int numAttributes = 25;
    private int startNumAttributes;
    private ArrayList<int[]> subsampleAtts;

    /**
     * Normalise outlier catch22 features which break on data not normalised
     */
    private boolean outlierNorm = true;

    /**
     * Use mean,stdev,slope as well as catch22 features
     */
    private boolean useSummaryStats = true;

    /** IntervalsFinders sets parameter values in buildClassifier if -1. */
    /**
     * Num intervals selected per tree built
     */
    private int numIntervals = -1;
    private transient Function<Integer, Integer> numIntervalsFinder;

    /** Secondary parameters */
    /** Mainly there to avoid single item intervals, which have no slope or std dev */
    /**
     * Min defaults to 3, Max defaults to m/2
     */
    private int minIntervalLength = -1;
    private transient Function<Integer, Integer> minIntervalLengthFinder;
    private int maxIntervalLength = -1;
    private transient Function<Integer, Integer> maxIntervalLengthFinder;

    /**
     * Ensemble members of base classifier, default to TimeSeriesTree
     */
    private ArrayList<Classifier> trees;
    private Classifier base = new ContinuousIntervalTree();

    /**
     * for each classifier [i]  interval j  starts at intervals.get(i)[j][0] and
     * ends  at  intervals.get(i)[j][1]
     */
    private ArrayList<int[][]> intervals;

    /**
     * Holding variable for test classification in order to retain the header info
     */
    private Instances testHolder;

    /**
     * Flags and data required if Bagging
     */
    private boolean bagging = false;
    private int[] oobCounts;
    private double[][] trainDistributions;

    /**
     * Flags and data required if Checkpointing
     */
    private boolean checkpoint = false;
    private String checkpointPath;
    private long checkpointTime = 0;
    private long lastCheckpointTime = 0;
    private long checkpointTimeDiff = 0;
    private boolean internalContractCheckpointHandling = false;

    /**
     * Flags and data required if Contracting
     */
    private boolean trainTimeContract = false;
    private long contractTime = 0;
    private int maxClassifiers = 500;

    /**
     * Multithreading
     */
    private int numThreads = 1;
    private boolean multiThread = false;
    private ExecutorService ex;

    /**
     * Visualisation and interpretability
     */
    private String visSavePath;
    private int visNumTopAtts = 3;
    private String interpSavePath;
    private ArrayList<ArrayList<double[]>> interpData;
    private ArrayList<Integer> interpTreePreds;
    private int interpCount = 0;
    private double[] interpSeries;
    private int interpPred;

    /**
     * data information
     */
    private int seriesLength;
    private int numInstances;

    /**
     * Multivariate
     */
    private int numDimensions;
    private ArrayList<int[]> intervalDimensions;

    /**
     * Transformer used to obtain catch22 features
     */
    private transient Catch22 c22;

    protected static final long serialVersionUID = 1L;

    /**
     * Default constructor for CIF. Can estimate own performance.
     */
    public CIF() {
        super(CAN_ESTIMATE_OWN_PERFORMANCE);
    }

    /**
     * Set the number of trees to be built.
     *
     * @param t number of trees
     */
    public void setNumTrees(int t) {
        numClassifiers = t;
    }

    /**
     * Set the number of attributes to be subsampled per tree.
     *
     * @param a number of attributes sumsampled
     */
    public void setAttSubsampleSize(int a) {
        attSubsampleSize = a;
    }

    /**
     * Set whether to use the original TSF statistics as well as catch22 features.
     *
     * @param b boolean to use summary stats
     */
    public void setUseSummaryStats(boolean b) {
        useSummaryStats = b;
    }

    /**
     * Set a function for finding the number of intervals randomly selected per tree.
     *
     * @param f a function for the number of intervals
     */
    public void setNumIntervalsFinder(Function<Integer, Integer> f) {
        numIntervalsFinder = f;
    }

    /**
     * Set a function for finding the min interval length for randomly selected intervals.
     *
     * @param f a function for min interval length
     */
    public void setMinIntervalLengthFinder(Function<Integer, Integer> f) {
        minIntervalLengthFinder = f;
    }

    /**
     * Set a function for finding the max interval length for randomly selected intervals.
     *
     * @param f a function for max interval length
     */
    public void setMaxIntervalLengthFinder(Function<Integer, Integer> f) {
        maxIntervalLengthFinder = f;
    }

    /**
     * Set whether to normalise the outlier catch22 features.
     *
     * @param b boolean to set outlier normalisation
     */
    public void setOutlierNorm(boolean b) {
        outlierNorm = b;
    }

    /**
     * Sets the base classifier for the ensemble.
     *
     * @param c a base classifier constructed elsewhere and cloned into ensemble
     */
    public void setBaseClassifier(Classifier c) {
        base = c;
    }

    /**
     * Set whether to perform bagging with replacement.
     *
     * @param b boolean to set bagging
     */
    public void setBagging(boolean b) {
        bagging = b;
    }

    /**
     * Set the number of attributes to show when creating visualisations.
     *
     * @param i number of attributes
     */
    public void setVisNumTopAtts(int i) {
        visNumTopAtts = i;
    }

    /**
     * Outputs CIF parameters information as a String.
     *
     * @return String written to results files
     */
    @Override //SaveParameterInfo
    public String getParameters() {
        int nt = numClassifiers;
        if (trees != null) nt = trees.size();
        return super.getParameters() + ",numTrees," + nt + ",attSubsampleSize," + attSubsampleSize +
                ",outlierNorm," + outlierNorm + ",basicSummaryStats," + useSummaryStats + ",numIntervals," + numIntervals +
                ",minIntervalLength," + minIntervalLength + ",maxIntervalLength," + maxIntervalLength +
                ",baseClassifier," + base.getClass().getSimpleName() + ",bagging," + bagging +
                ",estimator," + trainEstimateMethod.name() + ",contractTime," + contractTime;
    }

    /**
     * Returns the capabilities for CIF. These are that the
     * data must be numeric or relational, with no missing and a nominal class
     *
     * @return the capabilities of CIF
     */
    @Override //AbstractClassifier
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();

        result.setMinimumNumberInstances(2);

        // attributes
        result.enable(Capabilities.Capability.RELATIONAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);

        // class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);

        return result;
    }

    /**
     * Returns the time series capabilities for CIF. These are that the
     * data must be equal length, with no missing values
     *
     * @return the time series capabilities of CIF
     */
    public TSCapabilities getTSCapabilities() {
        TSCapabilities capabilities = new TSCapabilities();
        capabilities.enable(TSCapabilities.EQUAL_LENGTH)
                .enable(TSCapabilities.MULTI_OR_UNIVARIATE)
                .enable(TSCapabilities.NO_MISSING_VALUES)
                .enable(TSCapabilities.MIN_LENGTH(3));
        return capabilities;
    }

    /**
     * Build the CIF classifier.
     *
     * @param data TimeSeriesInstances object
     * @throws Exception unable to train model
     */
    @Override //TSClassifier
    public void buildClassifier(TimeSeriesInstances data) throws Exception {
        /** Build Stage:
         *  Builds the final classifier with or without bagging.
         */
        trainResults = new ClassifierResults();
        rand.setSeed(seed);
        numClasses = data.numClasses();
        trainResults.setEstimatorName(getClassifierName());
        trainResults.setBuildTime(System.nanoTime());
        // can classifier handle the data?
        getTSCapabilities().test(data);

        File file = new File(checkpointPath + "CIF" + seed + ".ser");
        //if checkpointing and serialised files exist load said files
        if (checkpoint && file.exists()) {
            //path checkpoint files will be saved to
            if (debug)
                System.out.println("Loading from checkpoint file");
            loadFromFile(checkpointPath + "CIF" + seed + ".ser");
        }
        //initialise variables
        else {
            seriesLength = data.getMaxLength();
            numInstances = data.numInstances();
            numDimensions = data.getMaxNumDimensions();

            if (numIntervalsFinder == null) {
                numIntervals = (int) (Math.sqrt(seriesLength) * Math.sqrt(numDimensions));
            } else {
                numIntervals = numIntervalsFinder.apply(seriesLength);
            }

            if (minIntervalLengthFinder == null) {
                minIntervalLength = 3;
            } else {
                minIntervalLength = minIntervalLengthFinder.apply(seriesLength);
            }
            if (minIntervalLength < 3) {
                minIntervalLength = 3;
            }
            if (seriesLength <= minIntervalLength) {
                minIntervalLength = seriesLength / 2;
            }

            if (maxIntervalLengthFinder == null) {
                maxIntervalLength = seriesLength / 2;
            } else {
                maxIntervalLength = maxIntervalLengthFinder.apply(seriesLength);
            }
            if (maxIntervalLength > seriesLength) {
                maxIntervalLength = seriesLength;
            }

            if (maxIntervalLength < minIntervalLength) {
                maxIntervalLength = minIntervalLength;
            }

            if (!useSummaryStats) {
                numAttributes = 22;
            }

            startNumAttributes = numAttributes;
            subsampleAtts = new ArrayList<>();

            if (attSubsampleSize < numAttributes) {
                numAttributes = attSubsampleSize;
            }

            //Set up for Bagging if required
            if (bagging && getEstimateOwnPerformance()) {
                trainDistributions = new double[numInstances][numClasses];
                oobCounts = new int[numInstances];
            }

            //cancel loop using time instead of number built.
            if (trainTimeContract) {
                numClassifiers = maxClassifiers;
                trees = new ArrayList<>();
                intervals = new ArrayList<>();
            } else {
                trees = new ArrayList<>(numClassifiers);
                intervals = new ArrayList<>(numClassifiers);
            }

            intervalDimensions = new ArrayList<>();
        }

        if (multiThread) {
            ex = Executors.newFixedThreadPool(numThreads);
            if (checkpoint) System.out.println("Unable to checkpoint until end of build when multi threading.");
        }

        c22 = new Catch22();
        c22.setOutlierNormalise(outlierNorm);

        //Set up instances size and format.
        ArrayList<Attribute> atts = new ArrayList<>();
        String name;
        for (int j = 0; j < numIntervals * numAttributes; j++) {
            name = "F" + j;
            atts.add(new Attribute(name));
        }
        //Get the class values as an array list
        ArrayList<String> vals = new ArrayList<>(numClasses);
        for (int j = 0; j < numClasses; j++)
            vals.add(Integer.toString(j));
        atts.add(new Attribute("cls", vals));
        //create blank instances with the correct class value
        Instances result = new Instances("Tree", atts, numInstances);
        result.setClassIndex(result.numAttributes() - 1);
        for (int i = 0; i < numInstances; i++) {
            DenseInstance in = new DenseInstance(result.numAttributes());
            in.setValue(result.numAttributes() - 1, data.get(i).getLabelIndex());
            result.add(in);
        }

        testHolder = new Instances(result, 1);
        DenseInstance in = new DenseInstance(testHolder.numAttributes());
        in.setValue(testHolder.numAttributes() - 1, -1);
        testHolder.add(in);

        if (multiThread) {
            multiThreadBuildCIF(data, result);
        } else {
            buildCIF(data, result);
        }

        if (trees.size() == 0) {//Not enough time to build a single classifier
            throw new Exception((" ERROR in CIF, no trees built, contract time probably too low. Contract time = "
                    + contractTime));
        }

        if (checkpoint) {
            saveToFile(checkpointPath);
        }

        trainResults.setTimeUnit(TimeUnit.NANOSECONDS);
        trainResults.setBuildTime(System.nanoTime() - trainResults.getBuildTime() - checkpointTimeDiff
                - trainResults.getErrorEstimateTime());

        if (getEstimateOwnPerformance()) {
            long est1 = System.nanoTime();
            estimateOwnPerformance(data);
            long est2 = System.nanoTime();
            trainResults.setErrorEstimateTime(est2 - est1 + trainResults.getErrorEstimateTime());
        }
        trainResults.setBuildPlusEstimateTime(trainResults.getBuildTime() + trainResults.getErrorEstimateTime());
        trainResults.setParas(getParameters());
        printLineDebug("*************** Finished CIF Build with " + trees.size() + " Trees built in " +
                trainResults.getBuildTime() / 1000000000 + " Seconds  ***************");
    }

    /**
     * Build the CIF classifier.
     *
     * @param data weka Instances object
     * @throws Exception unable to train model
     */
    @Override //AbstractClassifier
    public void buildClassifier(Instances data) throws Exception {
        buildClassifier(Converter.fromArff(data));
    }

    /**
     * Build the CIF classifier
     * For each base classifier
     *      generate random intervals
     *      do the transfrorms
     *      build the classifier
     *
     * @param data   TimeSeriesInstances data
     * @param result Instances object formatted for transformed data
     * @throws Exception unable to build CIF
     */
    public void buildCIF(TimeSeriesInstances data, Instances result) throws Exception {
        double[][][] dimensions = data.toValueArray();

        while (withinTrainContract(trainResults.getBuildTime()) && trees.size() < numClassifiers) {
            int i = trees.size();

            //1. Select random intervals for tree i

            int[][] interval = new int[numIntervals][2];  //Start and end

            for (int j = 0; j < numIntervals; j++) {
                if (rand.nextBoolean()) {
                    if (seriesLength - minIntervalLength > 0)
                        interval[j][0] = rand.nextInt(seriesLength - minIntervalLength); //Start point

                    int range = Math.min(seriesLength - interval[j][0], maxIntervalLength);
                    int length;
                    if (range - minIntervalLength == 0) length = minIntervalLength;
                    else length = rand.nextInt(range - minIntervalLength) + minIntervalLength;
                    interval[j][1] = interval[j][0] + length;
                } else {
                    if (seriesLength - minIntervalLength > 0)
                        interval[j][1] = rand.nextInt(seriesLength - minIntervalLength)
                                + minIntervalLength; //End point

                    int range = Math.min(interval[j][1], maxIntervalLength);
                    int length;
                    if (range - minIntervalLength == 0) length = minIntervalLength;
                    else length = rand.nextInt(range - minIntervalLength) + minIntervalLength;
                    interval[j][0] = interval[j][1] - length;
                }
            }

            //If bagging find instances with replacement
            int[] instInclusions = null;
            boolean[] inBag = null;
            if (bagging) {
                inBag = new boolean[numInstances];
                instInclusions = new int[numInstances];

                for (int n = 0; n < numInstances; n++) {
                    instInclusions[rand.nextInt(numInstances)]++;
                }

                for (int n = 0; n < numInstances; n++) {
                    if (instInclusions[n] > 0) {
                        inBag[n] = true;
                    }
                }
            }

            //find attributes to subsample
            ArrayList<Integer> arrl = new ArrayList<>(startNumAttributes);
            for (int n = 0; n < startNumAttributes; n++) {
                arrl.add(n);
            }

            int[] subsampleAtt = new int[numAttributes];
            for (int n = 0; n < numAttributes; n++) {
                subsampleAtt[n] = arrl.remove(rand.nextInt(arrl.size()));
            }

            //find dimensions for each interval
            int[] intervalDimension = new int[numIntervals];
            for (int n = 0; n < numIntervals; n++) {
                intervalDimension[n] = rand.nextInt(numDimensions);
            }
            Arrays.sort(intervalDimension);

            //For bagging
            int instIdx = 0;
            int lastIdx = -1;

            //2. Generate and store attributes
            for (int k = 0; k < numInstances; k++) {
                //For each instance
                if (bagging) {
                    boolean sameInst = false;

                    while (true) {
                        if (instInclusions[instIdx] == 0) {
                            instIdx++;
                        } else {
                            instInclusions[instIdx]--;

                            if (instIdx == lastIdx) {
                                result.set(k, new DenseInstance(result.instance(k - 1)));
                                sameInst = true;
                            } else {
                                lastIdx = instIdx;
                            }

                            break;
                        }
                    }

                    if (sameInst) continue;

                    result.instance(k).setValue(result.classIndex(), data.get(instIdx).getLabelIndex());
                } else {
                    instIdx = k;
                }

                for (int j = 0; j < numIntervals; j++) {
                    //extract the interval
                    double[] series = dimensions[instIdx][intervalDimension[j]];
                    double[] intervalArray = Arrays.copyOfRange(series, interval[j][0], interval[j][1] + 1);

                    //process features

                    for (int g = 0; g < numAttributes; g++) {
                        if (subsampleAtt[g] < 22) {
                            result.instance(k).setValue(j * numAttributes + g,
                                    c22.getSummaryStatByIndex(subsampleAtt[g], j, intervalArray));
                        } else {
                            result.instance(k).setValue(j * numAttributes + g,
                                    FeatureSet.calcFeatureByIndex(subsampleAtt[g], interval[j][0],
                                            interval[j][1], series));
                        }
                    }
                }
            }

            //3. Create and build tree using all the features. Feature selection
            Classifier tree = AbstractClassifier.makeCopy(base);
            if (seedClassifier && tree instanceof Randomizable)
                ((Randomizable) tree).setSeed(seed * (i + 1));

            tree.buildClassifier(result);

            if (bagging && getEstimateOwnPerformance()) {
                long t1 = System.nanoTime();

                if (base instanceof ContinuousIntervalTree) {
                    for (int n = 0; n < numInstances; n++) {
                        if (inBag[n])
                            continue;

                        double[] newProbs = ((ContinuousIntervalTree) tree).distributionForInstance(dimensions[n],
                                functions, interval, subsampleAtt, intervalDimension);
                        oobCounts[n]++;
                        for (int k = 0; k < newProbs.length; k++)
                            trainDistributions[n][k] += newProbs[k];
                    }
                } else {
                    for (int n = 0; n < numInstances; n++) {
                        if (inBag[n])
                            continue;

                        for (int j = 0; j < numIntervals; j++) {
                            double[] series = dimensions[n][intervalDimension[j]];
                            double[] intervalArray = Arrays.copyOfRange(series, interval[j][0], interval[j][1] + 1);

                            for (int g = 0; g < numAttributes; g++) {
                                if (subsampleAtt[g] < 22) {
                                    testHolder.instance(0).setValue(j * numAttributes + g,
                                            c22.getSummaryStatByIndex(subsampleAtt[g], j, intervalArray));
                                } else {
                                    testHolder.instance(0).setValue(j * numAttributes + g,
                                            FeatureSet.calcFeatureByIndex(subsampleAtt[g], interval[j][0],
                                                    interval[j][1], series));
                                }
                            }
                        }

                        double[] newProbs = tree.distributionForInstance(testHolder.instance(0));
                        oobCounts[n]++;
                        for (int k = 0; k < newProbs.length; k++)
                            trainDistributions[n][k] += newProbs[k];
                    }
                }

                trainResults.setErrorEstimateTime(trainResults.getErrorEstimateTime() + (System.nanoTime() - t1));
            }

            trees.add(tree);
            intervals.add(interval);
            subsampleAtts.add(subsampleAtt);
            intervalDimensions.add(intervalDimension);

            //Timed checkpointing if enabled, else checkpoint every 100 trees
            if (checkpoint && ((checkpointTime > 0 && System.nanoTime() - lastCheckpointTime > checkpointTime)
                    || trees.size() % 100 == 0)) {
                saveToFile(checkpointPath);
            }
        }
    }

    /**
     * Build the CIF classifier using multiple threads.
     * Unable to checkpoint until after the build process while using multiple threads.
     * For each base classifier
     *      generate random intervals
     *      do the transfrorms
     *      build the classifier
     *
     * @param data   TimeSeriesInstances data
     * @param result Instances object formatted for transformed data
     * @throws Exception unable to build CIF
     */
    private void multiThreadBuildCIF(TimeSeriesInstances data, Instances result) throws Exception {
        double[][][] dimensions = data.toValueArray();
        int[] classVals = data.getClassIndexes();
        int buildStep = trainTimeContract ? numThreads : numClassifiers;

        while (withinTrainContract(trainResults.getBuildTime()) && trees.size() < numClassifiers) {
            ArrayList<Future<MultiThreadBuildHolder>> futures = new ArrayList<>(buildStep);

            int end = trees.size() + buildStep;
            for (int i = trees.size(); i < end; ++i) {
                Instances resultCopy = new Instances(result, numInstances);
                for (int n = 0; n < numInstances; n++) {
                    DenseInstance in = new DenseInstance(result.numAttributes());
                    in.setValue(result.numAttributes() - 1, result.instance(n).classValue());
                    resultCopy.add(in);
                }

                futures.add(ex.submit(new TreeBuildThread(i, dimensions, classVals, resultCopy)));
            }

            for (Future<MultiThreadBuildHolder> f : futures) {
                MultiThreadBuildHolder h = f.get();
                trees.add(h.tree);
                intervals.add(h.interval);
                subsampleAtts.add(h.subsampleAtts);
                intervalDimensions.add(h.intervalDimensions);

                if (bagging && getEstimateOwnPerformance()) {
                    trainResults.setErrorEstimateTime(trainResults.getErrorEstimateTime() + h.errorTime);
                    for (int n = 0; n < numInstances; n++) {
                        oobCounts[n] += h.oobCounts[n];
                        for (int k = 0; k < numClasses; k++)
                            trainDistributions[n][k] += h.trainDistribution[n][k];
                    }
                }
            }
        }
    }

    /**
     * Estimate accuracy stage: Three scenarios
     * 1. If we bagged the full build (bagging ==true), we estimate using the full build OOB.
     * If we built on all data (bagging ==false) we estimate either:
     * 2. With a 10 fold CV.
     * 3. Build a bagged model simply to get the estimate.
     *
     * @param data TimeSeriesInstances to estimate with
     * @throws Exception unable to obtain estimate
     */
    private void estimateOwnPerformance(TimeSeriesInstances data) throws Exception {
        if (bagging) {
            // Use bag data, counts normalised to probabilities
            double[] preds = new double[data.numInstances()];
            double[] actuals = new double[data.numInstances()];
            long[] predTimes = new long[data.numInstances()];//Dummy variable, need something
            for (int j = 0; j < data.numInstances(); j++) {
                long predTime = System.nanoTime();
                if (oobCounts[j] == 0)
                    Arrays.fill(trainDistributions[j], 1.0 / trainDistributions[j].length);
                else
                    for (int k = 0; k < trainDistributions[j].length; k++)
                        trainDistributions[j][k] /= oobCounts[j];
                preds[j] = findIndexOfMax(trainDistributions[j], rand);
                actuals[j] = data.get(j).getLabelIndex();
                predTimes[j] = System.nanoTime() - predTime;
            }
            trainResults.addAllPredictions(actuals, preds, trainDistributions, predTimes, null);
            trainResults.setEstimatorName("CIFBagging");
            trainResults.setDatasetName(data.getProblemName());
            trainResults.setSplit("train");
            trainResults.setFoldID(seed);
            trainResults.setErrorEstimateMethod("OOB");
            trainResults.finaliseResults(actuals);
        }
        //Either do a CV, or bag and get the estimates
        else if (trainEstimateMethod == TrainEstimateMethod.CV) {
            /** Defaults to 10 or numInstances, whichever is smaller.
             * Interface TrainAccuracyEstimate
             * Could this be handled better? */
            int numFolds = Math.min(data.numInstances(), 10);
            CrossValidationEvaluator cv = new CrossValidationEvaluator();
            if (seedClassifier)
                cv.setSeed(seed * 5);
            cv.setNumFolds(numFolds);
            CIF cif = new CIF();
            cif.copyParameters(this);
            if (seedClassifier)
                cif.setSeed(seed * 100);
            cif.setEstimateOwnPerformance(false);
            long tt = trainResults.getBuildTime();
            trainResults = cv.evaluate(cif, Converter.toArff(data));
            trainResults.setBuildTime(tt);
            trainResults.setEstimatorName("CIFCV");
            trainResults.setErrorEstimateMethod("CV_" + numFolds);
        } else if (trainEstimateMethod == TrainEstimateMethod.OOB || trainEstimateMethod == TrainEstimateMethod.NONE ||
                trainEstimateMethod == TrainEstimateMethod.TRAIN) {
            /** Build a single new TSF using Bagging, and extract the estimate from this
             */
            CIF cif = new CIF();
            cif.copyParameters(this);
            cif.setSeed(seed);
            cif.setEstimateOwnPerformance(true);
            cif.bagging = true;
            cif.multiThread = multiThread;
            cif.numThreads = numThreads;
            cif.buildClassifier(data);
            long tt = trainResults.getBuildTime();
            trainResults = cif.trainResults;
            trainResults.setBuildTime(tt);
            trainResults.setEstimatorName("CIFOOB");
            trainResults.setErrorEstimateMethod("OOB");
        }
    }

    /**
     * Copy the parameters of a CIF object to this.
     *
     * @param other A CIF object
     */
    private void copyParameters(CIF other) {
        this.numClassifiers = other.numClassifiers;
        this.attSubsampleSize = other.attSubsampleSize;
        this.outlierNorm = other.outlierNorm;
        this.useSummaryStats = other.useSummaryStats;
        this.numIntervals = other.numIntervals;
        this.numIntervalsFinder = other.numIntervalsFinder;
        this.minIntervalLength = other.minIntervalLength;
        this.minIntervalLengthFinder = other.minIntervalLengthFinder;
        this.maxIntervalLength = other.maxIntervalLength;
        this.maxIntervalLengthFinder = other.maxIntervalLengthFinder;
        this.base = other.base;
        this.bagging = other.bagging;
        this.trainTimeContract = other.trainTimeContract;
        this.contractTime = other.contractTime;
    }

    /**
     * Find class probabilities of an instance using the trained model.
     *
     * @param ins TimeSeriesInstance object
     * @return array of doubles: probability of each class
     * @throws Exception failure to classify
     */
    @Override //TSClassifier
    public double[] distributionForInstance(TimeSeriesInstance ins) throws Exception {
        double[] d = new double[numClasses];
        double[][] dimensions = ins.toValueArray();

        if (interpSavePath != null) {
            interpData = new ArrayList<>();
            interpTreePreds = new ArrayList<>();
        }

        if (multiThread) {
            ArrayList<Future<MultiThreadPredictionHolder>> futures = new ArrayList<>(trees.size());

            for (int i = 0; i < trees.size(); ++i) {
                Instances testCopy = new Instances(testHolder, 1);
                DenseInstance in = new DenseInstance(testHolder.numAttributes());
                in.setValue(testHolder.numAttributes() - 1, -1);
                testCopy.add(in);

                futures.add(ex.submit(new TreePredictionThread(i, dimensions, trees.get(i), testCopy)));
            }

            for (Future<MultiThreadPredictionHolder> f : futures) {
                MultiThreadPredictionHolder h = f.get();
                d[h.c]++;

                if (interpSavePath != null && base instanceof ContinuousIntervalTree) {
                    interpData.add(h.al);
                    interpTreePreds.add(h.c);
                }
            }
        } else if (base instanceof ContinuousIntervalTree) {
            for (int i = 0; i < trees.size(); i++) {
                int c;
                if (interpSavePath != null) {
                    ArrayList<double[]> al = new ArrayList<>();
                    c = (int) ((ContinuousIntervalTree) trees.get(i)).classifyInstance(dimensions, functions,
                            intervals.get(i), subsampleAtts.get(i), intervalDimensions.get(i), al);
                    interpData.add(al);
                    interpTreePreds.add(c);
                } else {
                    c = (int) ((ContinuousIntervalTree) trees.get(i)).classifyInstance(dimensions, functions,
                            intervals.get(i), subsampleAtts.get(i), intervalDimensions.get(i));
                }
                d[c]++;
            }
        } else {
            //Build transformed instance
            for (int i = 0; i < trees.size(); i++) {
                Catch22 c22 = new Catch22();
                c22.setOutlierNormalise(outlierNorm);

                for (int j = 0; j < numIntervals; j++) {
                    double[] series = dimensions[intervalDimensions.get(i)[j]];
                    double[] intervalArray = Arrays.copyOfRange(series, intervals.get(i)[j][0],
                            intervals.get(i)[j][1] + 1);

                    for (int g = 0; g < numAttributes; g++) {
                        if (subsampleAtts.get(i)[g] < 22) {
                            testHolder.instance(0).setValue(j * numAttributes + g,
                                    c22.getSummaryStatByIndex(subsampleAtts.get(i)[g], j, intervalArray));
                        } else {
                            testHolder.instance(0).setValue(j * numAttributes + g,
                                    FeatureSet.calcFeatureByIndex(subsampleAtts.get(i)[g], intervals.get(i)[j][0],
                                            intervals.get(i)[j][1], series));
                        }
                    }
                }

                int c = (int) trees.get(i).classifyInstance(testHolder.instance(0));
                d[c]++;
            }
        }

        double sum = 0;
        for (double x : d)
            sum += x;
        for (int i = 0; i < d.length; i++)
            d[i] = d[i] / sum;

        if (interpSavePath != null) {
            interpSeries = dimensions[0];
            interpPred = argMax(d, rand);
        }

        return d;
    }

    /**
     * Find class probabilities of an instance using the trained model.
     *
     * @param ins weka Instance object
     * @return array of doubles: probability of each class
     * @throws Exception failure to classify
     */
    @Override //AbstractClassifier
    public double[] distributionForInstance(Instance ins) throws Exception {
        return distributionForInstance(Converter.fromArff(ins));
    }

    /**
     * Classify an instance using the trained model.
     *
     * @param ins TimeSeriesInstance object
     * @return predicted class value
     * @throws Exception failure to classify
     */
    @Override //TSClassifier
    public double classifyInstance(TimeSeriesInstance ins) throws Exception {
        double[] probs = distributionForInstance(ins);
        return findIndexOfMax(probs, rand);
    }

    /**
     * Classify an instance using the trained model.
     *
     * @param ins weka Instance object
     * @return predicted class value
     * @throws Exception failure to classify
     */
    @Override //AbstractClassifier
    public double classifyInstance(Instance ins) throws Exception {
        return classifyInstance(Converter.fromArff(ins));
    }

    /**
     * Set the train time limit for a contracted classifier.
     *
     * @param amount contract time in nanoseconds
     */
    @Override //TrainTimeContractable
    public void setTrainTimeLimit(long amount) {
        contractTime = amount;
        trainTimeContract = true;
    }

    /**
     * Check if a contracted classifier is within its train time limit.
     *
     * @param start classifier build start time
     * @return true if within the contract or not contracted, false otherwise.
     */
    @Override //TrainTimeContractable
    public boolean withinTrainContract(long start) {
        if (contractTime <= 0) return true; //Not contracted
        return System.nanoTime() - start - checkpointTimeDiff < contractTime;
    }

    /**
     * Set the path to save checkpoint files to.
     *
     * @param path string for full path for the directory to store checkpointed files
     * @return true if valid path, false otherwise
     */
    @Override //Checkpointable
    public boolean setCheckpointPath(String path) {
        boolean validPath = Checkpointable.super.createDirectories(path);
        if (validPath) {
            checkpointPath = path;
            checkpoint = true;
        }
        return validPath;
    }

    /**
     * Set the time between checkpoints in hours.
     *
     * @param t number of hours between checkpoints
     * @return true
     */
    @Override //Checkpointable
    public boolean setCheckpointTimeHours(int t) {
        checkpointTime = TimeUnit.NANOSECONDS.convert(t, TimeUnit.HOURS);
        return true;
    }

    /**
     * Serialises this CIF object to the specified path.
     *
     * @param path save path for object
     * @throws Exception object fails to save
     */
    @Override //Checkpointable
    public void saveToFile(String path) throws Exception {
        lastCheckpointTime = System.nanoTime();
        Checkpointable.super.saveToFile(path + "CIF" + seed + "temp.ser");
        File file = new File(path + "CIF" + seed + "temp.ser");
        File file2 = new File(path + "CIF" + seed + ".ser");
        file2.delete();
        file.renameTo(file2);
        if (internalContractCheckpointHandling) checkpointTimeDiff += System.nanoTime() - lastCheckpointTime;
    }

    /**
     * Copies values from a loaded CIF object into this object.
     *
     * @param obj a CIF object
     * @throws Exception if obj is not an instance of CIF
     */
    @Override //Checkpointable
    public void copyFromSerObject(Object obj) throws Exception {
        if (!(obj instanceof CIF))
            throw new Exception("The SER file is not an instance of TSF");
        CIF saved = ((CIF) obj);
        System.out.println("Loading CIF" + seed + ".ser");

        try {
            numClassifiers = saved.numClassifiers;
            attSubsampleSize = saved.attSubsampleSize;
            numAttributes = saved.numAttributes;
            startNumAttributes = saved.startNumAttributes;
            subsampleAtts = saved.subsampleAtts;
            outlierNorm = saved.outlierNorm;
            useSummaryStats = saved.useSummaryStats;
            numIntervals = saved.numIntervals;
            //numIntervalsFinder = saved.numIntervalsFinder;
            minIntervalLength = saved.minIntervalLength;
            //minIntervalLengthFinder = saved.minIntervalLengthFinder;
            maxIntervalLength = saved.maxIntervalLength;
            //maxIntervalLengthFinder = saved.maxIntervalLengthFinder;
            trees = saved.trees;
            base = saved.base;
            intervals = saved.intervals;
            //testHolder = saved.testHolder;
            bagging = saved.bagging;
            oobCounts = saved.oobCounts;
            trainDistributions = saved.trainDistributions;
            //checkpoint = saved.checkpoint;
            //checkpointPath = saved.checkpointPath
            //checkpointTime = saved.checkpointTime;
            //lastCheckpointTime = saved.lastCheckpointTime;
            //checkpointTimeDiff = saved.checkpointTimeDiff;
            //internalContractCheckpointHandling = saved.internalContractCheckpointHandling;
            trainTimeContract = saved.trainTimeContract;
            if (internalContractCheckpointHandling) contractTime = saved.contractTime;
            maxClassifiers = saved.maxClassifiers;
            //numThreads = saved.numThreads;
            //multiThread = saved.multiThread;
            //ex = saved.ex;
            visSavePath = saved.visSavePath;
            visNumTopAtts = saved.visNumTopAtts;
            interpSavePath = saved.interpSavePath;
            //interpData = saved.interpData;
            //interpTreePreds = saved.interpTreePreds;
            //interpCount = saved.interpCount;
            //interpSeries = saved.interpSeries;
            //interpPred = saved.interpPred;
            seriesLength = saved.seriesLength;
            numInstances = saved.numInstances;
            numDimensions = saved.numDimensions;
            intervalDimensions = saved.intervalDimensions;
            //c22 = saved.c22;

            trainResults = saved.trainResults;
            if (!internalContractCheckpointHandling) trainResults.setBuildTime(System.nanoTime());
            seedClassifier = saved.seedClassifier;
            seed = saved.seed;
            rand = saved.rand;
            estimateOwnPerformance = saved.estimateOwnPerformance;
            trainEstimateMethod = saved.trainEstimateMethod;
            numClasses = saved.numClasses;

            if (internalContractCheckpointHandling) checkpointTimeDiff = saved.checkpointTimeDiff
                    + (System.nanoTime() - saved.lastCheckpointTime);
            lastCheckpointTime = System.nanoTime();
        } catch (Exception ex) {
            System.out.println("Unable to assign variables when loading serialised file");
        }
    }

    /**
     * Returns the default set of possible parameter values for use in setOptions when tuning.
     *
     * @return default parameter space for tuning
     */
    @Override //Tunable
    public ParameterSpace getDefaultParameterSearchSpace() {
        ParameterSpace ps = new ParameterSpace();
        String[] numAtts = {"8", "16", "25"};
        ps.addParameter("-A", numAtts);
        String[] maxIntervalLengths = {"0.5", "0.75", "1"};
        ps.addParameter("-L", maxIntervalLengths);
        return ps;
    }

    /**
     * Parses a given list of options. Valid options are:
     * <p>
     * -A  The number of attributes to subsample as an integer from 1-25.
     * -L  Max interval length as a proportion of series length as a double from 0-1.
     *
     * @param options the list of options as an array of strings
     * @throws Exception if an option value is invalid
     */
    @Override //AbstractClassifier
    public void setOptions(String[] options) throws Exception {
        System.out.println(Arrays.toString(options));

        String numAttsString = Utils.getOption("-A", options);
        System.out.println(numAttsString);
        if (numAttsString.length() != 0)
            attSubsampleSize = Integer.parseInt(numAttsString);

        String maxIntervalLengthsString = Utils.getOption("-L", options);
        System.out.println(maxIntervalLengthsString);
        if (maxIntervalLengthsString.length() != 0)
            maxIntervalLengthFinder = (numAtts) -> (int) (numAtts * Double.parseDouble(maxIntervalLengthsString));

        System.out.println(attSubsampleSize + " " + maxIntervalLengthFinder.apply(100));
    }

    /**
     * Enables multi threading with a set number of threads to use.
     *
     * @param numThreads number of threads available for multi threading
     */
    @Override //MultiThreadable
    public void enableMultiThreading(int numThreads) {
        if (numThreads > 1) {
            this.numThreads = numThreads;
            multiThread = true;
        } else {
            this.numThreads = 1;
            multiThread = false;
        }
    }

    /**
     * Creates and stores a path to save visualisation files to.
     *
     * @param path String directory path
     * @return true if path is valid, false otherwise.
     */
    @Override //Visualisable
    public boolean setVisualisationSavePath(String path) {
        boolean validPath = Visualisable.super.createVisualisationDirectories(path);
        if (validPath) {
            visSavePath = path;
        }
        return validPath;
    }

    /**
     * Finds the temporal importance curves for model. Outputs a matplotlib figure using the visCIF.py file using the
     * generated curves.
     *
     * @return true if python file to create visualisation ran, false if no path set or invalid classifier
     * @throws Exception if failure to set path or create visualisation
     */
    @Override //Visualisable
    public boolean createVisualisation() throws Exception {
        if (!(base instanceof ContinuousIntervalTree)) {
            System.err.println("CIF temporal importance curve only available for ContinuousIntervalTree.");
            return false;
        }

        if (visSavePath == null) {
            System.err.println("CIF visualisation save path not set.");
            return false;
        }

        boolean isMultivariate = numDimensions > 1;
        int[] dimCount = null;
        if (isMultivariate) dimCount = new int[numDimensions];

        //get information gain from all tree node splits for each attribute/time point
        double[][][] curves = new double[startNumAttributes][numDimensions][seriesLength];
        for (int i = 0; i < trees.size(); i++) {
            ContinuousIntervalTree tree = (ContinuousIntervalTree) trees.get(i);
            ArrayList<Double>[] sg = tree.getTreeSplitsGain();

            for (int n = 0; n < sg[0].size(); n++) {
                double split = sg[0].get(n);
                double gain = sg[1].get(n);
                int interval = (int) (split / numAttributes);
                int att = subsampleAtts.get(i)[(int) (split % numAttributes)];
                int dim = intervalDimensions.get(i)[interval];

                if (isMultivariate) dimCount[dim]++;

                for (int j = intervals.get(i)[interval][0]; j <= intervals.get(i)[interval][1]; j++) {
                    curves[att][dim][j] += gain;
                }
            }
        }

        if (isMultivariate) {
            OutFile of = new OutFile(visSavePath + "/dims" + seed + ".txt");
            of.writeLine(Arrays.toString(dimCount));
            of.closeFile();
        }

        OutFile of = new OutFile(visSavePath + "/vis" + seed + ".txt");
        for (int i = 0; i < numDimensions; i++) {
            for (int n = 0; n < startNumAttributes; n++) {
                switch (n) {
                    case 22:
                        of.writeLine("Mean");
                        break;
                    case 23:
                        of.writeLine("Standard Deviation");
                        break;
                    case 24:
                        of.writeLine("Slope");
                        break;
                    default:
                        of.writeLine(Catch22.getSummaryStatNameByIndex(n));
                }
                of.writeLine(Integer.toString(i));
                of.writeLine(Arrays.toString(curves[n][i]));
            }
        }
        of.closeFile();

        //run python file to output temporal importance curves graph
        Process p = Runtime.getRuntime().exec("py src/main/python/visualisation/visCIF.py \"" +
                visSavePath.replace("\\", "/") + "\" " + seed + " " + startNumAttributes
                + " " + numDimensions + " " + visNumTopAtts);

        if (debug) {
            System.out.println("CIF vis python output:");
            BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            System.out.println("output : ");
            String outLine = out.readLine();
            while (outLine != null) {
                System.out.println(outLine);
                outLine = out.readLine();
            }
            System.out.println("error : ");
            String errLine = err.readLine();
            while (errLine != null) {
                System.out.println(errLine);
                errLine = err.readLine();
            }
        }

        return true;
    }

    /**
     * Stores a path to save interpretability files to.
     *
     * @param path String directory path
     * @return true if path is valid, false otherwise.
     */
    @Override //Interpretable
    public boolean setInterpretabilitySavePath(String path) {
        boolean validPath = Interpretable.super.createInterpretabilityDirectories(path);
        if (validPath) {
            interpSavePath = path;
        }
        return validPath;
    }

    /**
     * Outputs a summary/visualisation of how the last classifier prediction was made to a set path. Runs
     * interpretabilityCIF.py for visualisations.
     *
     * @return true if python file to create visualisation ran, false if no path set or invalid classifier
     * @throws Exception if failure to set path or output files
     */
    @Override //Interpretable
    public boolean lastClassifiedInterpretability() throws Exception {
        if (!(base instanceof ContinuousIntervalTree)) {
            System.err.println("CIF interpretability output only available for ContinuousIntervalTree.");
            return false;
        }

        if (interpSavePath == null) {
            System.err.println("CIF interpretability output save path not set.");
            return false;
        }

        OutFile of = new OutFile(interpSavePath + "pred" + seed + "-" + interpCount
                + ".txt");
        //output test series
        of.writeLine("Series");
        of.writeLine(Arrays.toString(interpSeries));
        //output the nodes visited for each tree
        for (int i = 0; i < interpData.size(); i++) {
            of.writeLine("Tree " + i + " - " + interpData.get(i).size() + " nodes - pred " + interpTreePreds.get(i));
            for (int n = 0; n < interpData.get(i).size(); n++) {
                if (n == interpData.get(i).size() - 1) {
                    of.writeLine(Arrays.toString(interpData.get(i).get(n)));
                } else {
                    ContinuousIntervalTree tree = (ContinuousIntervalTree) trees.get(i);
                    double[] arr = new double[5];
                    double[] nodeData = interpData.get(i).get(n);

                    int interval = (int) (nodeData[0] / numAttributes);
                    int att = (int) (nodeData[0] % numAttributes);
                    att = subsampleAtts.get(i)[att];

                    arr[0] = att;
                    arr[1] = intervals.get(i)[interval][0];
                    arr[2] = intervals.get(i)[interval][1];
                    arr[3] = nodeData[1];
                    arr[4] = nodeData[2];

                    of.writeLine(Arrays.toString(arr));
                }
            }
        }
        of.closeFile();

        //run python file to output graph displaying important attributes and intervals for test series
        Process p = Runtime.getRuntime().exec("py src/main/python/visualisation/interpretabilityCIF.py \"" +
                interpSavePath.replace("\\", "/") + "\" " + seed + " " + interpCount
                + " " + trees.size() + " " + seriesLength + " " + startNumAttributes + " " + interpPred);

        interpCount++;

        if (debug) {
            System.out.println("CIF interp python output:");
            BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            System.out.println("output : ");
            String outLine = out.readLine();
            while (outLine != null) {
                System.out.println(outLine);
                outLine = out.readLine();
            }
            System.out.println("error : ");
            String errLine = err.readLine();
            while (errLine != null) {
                System.out.println(errLine);
                errLine = err.readLine();
            }
        }

        return true;
    }

    /**
     * Get a unique indentifier for the last prediction made, used for filenames etc.
     *
     * @return int ID for the last prediction
     */
    @Override //Interpretable
    public int getPredID() {
        return interpCount;
    }

    /**
     * Nested class to find and store three simple summary features for an interval
     */
    private static class FeatureSet {
        public static double calcFeatureByIndex(int idx, int start, int end, double[] data) {
            switch (idx) {
                case 22:
                    return calcMean(start, end, data);
                case 23:
                    return calcStandardDeviation(start, end, data);
                case 24:
                    return calcSlope(start, end, data);
                default:
                    return Double.NaN;
            }
        }

        public static double calcMean(int start, int end, double[] data) {
            double sumY = 0;
            for (int i = start; i <= end; i++) {
                sumY += data[i];
            }

            int length = end - start + 1;
            return sumY / length;
        }

        public static double calcStandardDeviation(int start, int end, double[] data) {
            double sumY = 0;
            double sumYY = 0;
            for (int i = start; i <= end; i++) {
                sumY += data[i];
                sumYY += data[i] * data[i];
            }

            int length = end - start + 1;
            return (sumYY - (sumY * sumY) / length) / (length - 1);
        }

        public static double calcSlope(int start, int end, double[] data) {
            double sumY = 0;
            double sumX = 0, sumXX = 0, sumXY = 0;
            for (int i = start; i <= end; i++) {
                sumY += data[i];
                sumX += (i - start);
                sumXX += (i - start) * (i - start);
                sumXY += data[i] * (i - start);
            }

            int length = end - start + 1;
            double slope = (sumXY - (sumX * sumY) / length);
            double denom = sumXX - (sumX * sumX) / length;
            slope = denom == 0 ? 0 : slope / denom;
            return slope;
        }
    }

    /**
     * Class to hold data about a CIF tree when multi threading.
     */
    private static class MultiThreadBuildHolder {
        int[] subsampleAtts;
        int[] intervalDimensions;
        Classifier tree;
        int[][] interval;

        double[][] trainDistribution;
        int[] oobCounts;
        long errorTime;

        public MultiThreadBuildHolder() {
        }
    }

    /**
     * Class to build a CIF tree when multi threading.
     */
    private class TreeBuildThread implements Callable<MultiThreadBuildHolder> {
        int i;
        double[][][] dimensions;
        int[] classVals;
        Instances result;

        public TreeBuildThread(int i, double[][][] dimensions, int[] classVals, Instances result) {
            this.i = i;
            this.dimensions = dimensions;
            this.classVals = classVals;
            this.result = result;
        }

        /**
         * generate random intervals
         * do the transfrorms
         * build the classifier
         **/
        @Override
        public MultiThreadBuildHolder call() throws Exception {
            MultiThreadBuildHolder h = new MultiThreadBuildHolder();
            Random rand = new Random(seed + i * numClassifiers);

            Catch22 c22 = new Catch22();
            c22.setOutlierNormalise(outlierNorm);

            //1. Select random intervals for tree i
            int[][] interval = new int[numIntervals][2];  //Start and end

            for (int j = 0; j < numIntervals; j++) {
                if (rand.nextBoolean()) {
                    if (seriesLength - minIntervalLength > 0)
                        interval[j][0] = rand.nextInt(seriesLength - minIntervalLength); //Start point

                    int range = Math.min(seriesLength - interval[j][0], maxIntervalLength);
                    int length = rand.nextInt(range - minIntervalLength) + minIntervalLength;
                    interval[j][1] = interval[j][0] + length;
                } else {
                    if (seriesLength - minIntervalLength > 0)
                        interval[j][1] = rand.nextInt(seriesLength - minIntervalLength)
                                + minIntervalLength; //End point

                    int range = Math.min(interval[j][1], maxIntervalLength);
                    int length;
                    if (range - minIntervalLength == 0) length = 3;
                    else length = rand.nextInt(range - minIntervalLength) + minIntervalLength;
                    interval[j][0] = interval[j][1] - length;
                }
            }

            //If bagging find instances with replacement

            int[] instInclusions = null;
            boolean[] inBag = null;
            if (bagging) {
                inBag = new boolean[numInstances];
                instInclusions = new int[numInstances];

                for (int n = 0; n < numInstances; n++) {
                    instInclusions[rand.nextInt(numInstances)]++;
                }

                for (int n = 0; n < numInstances; n++) {
                    if (instInclusions[n] > 0) {
                        inBag[n] = true;
                    }
                }
            }

            //find attributes to subsample
            ArrayList<Integer> arrl = new ArrayList<>(startNumAttributes);
            for (int n = 0; n < startNumAttributes; n++) {
                arrl.add(n);
            }

            int[] subsampleAtts = new int[numAttributes];
            for (int n = 0; n < numAttributes; n++) {
                subsampleAtts[n] = arrl.remove(rand.nextInt(arrl.size()));
            }

            //find dimensions for each interval
            int[] intervalDimensions = new int[numIntervals];
            for (int n = 0; n < numIntervals; n++) {
                intervalDimensions[n] = rand.nextInt(numDimensions);
            }
            Arrays.sort(intervalDimensions);

            h.subsampleAtts = subsampleAtts;
            h.intervalDimensions = intervalDimensions;

            //For bagging
            int instIdx = 0;
            int lastIdx = -1;

            //2. Generate and store attributes
            for (int k = 0; k < numInstances; k++) {
                //For each instance

                if (bagging) {
                    boolean sameInst = false;

                    while (true) {
                        if (instInclusions[instIdx] == 0) {
                            instIdx++;
                        } else {
                            instInclusions[instIdx]--;

                            if (instIdx == lastIdx) {
                                result.set(k, new DenseInstance(result.instance(k - 1)));
                                sameInst = true;
                            } else {
                                lastIdx = instIdx;
                            }

                            break;
                        }
                    }

                    if (sameInst) continue;

                    result.instance(k).setValue(result.classIndex(), classVals[instIdx]);
                } else {
                    instIdx = k;
                }

                for (int j = 0; j < numIntervals; j++) {
                    //extract the interval
                    double[] series = dimensions[instIdx][intervalDimensions[j]];
                    double[] intervalArray = Arrays.copyOfRange(series, interval[j][0], interval[j][1] + 1);

                    for (int g = 0; g < numAttributes; g++) {
                        //process features
                        if (subsampleAtts[g] < 22) {
                            result.instance(k).setValue(j * numAttributes + g,
                                    c22.getSummaryStatByIndex(subsampleAtts[g], j, intervalArray));
                        } else {
                            result.instance(k).setValue(j * numAttributes + g,
                                    FeatureSet.calcFeatureByIndex(subsampleAtts[g], interval[j][0],
                                            interval[j][1], series));
                        }
                    }
                }
            }

            //3. Create and build tree using all the features. Feature selection
            Classifier tree = AbstractClassifier.makeCopy(base);
            if (seedClassifier && tree instanceof Randomizable)
                ((Randomizable) tree).setSeed(seed * (i + 1));

            tree.buildClassifier(result);

            if (bagging && getEstimateOwnPerformance()) {
                long t1 = System.nanoTime();
                int[] oobCounts = new int[numInstances];
                double[][] trainDistributions = new double[numInstances][numClasses];

                if (base instanceof ContinuousIntervalTree) {
                    for (int n = 0; n < numInstances; n++) {
                        if (inBag[n])
                            continue;

                        double[] newProbs = ((ContinuousIntervalTree) tree).distributionForInstance(dimensions[n],
                                functions, interval, subsampleAtts, intervalDimensions);
                        oobCounts[n]++;
                        for (int k = 0; k < newProbs.length; k++)
                            trainDistributions[n][k] += newProbs[k];
                    }
                } else {
                    for (int n = 0; n < numInstances; n++) {
                        if (inBag[n])
                            continue;

                        for (int j = 0; j < numIntervals; j++) {
                            double[] series = dimensions[n][intervalDimensions[j]];
                            double[] intervalArray = Arrays.copyOfRange(series, interval[j][0], interval[j][1] + 1);

                            for (int g = 0; g < numAttributes; g++) {
                                if (subsampleAtts[g] < 22) {
                                    result.instance(0).setValue(j * numAttributes + g,
                                            c22.getSummaryStatByIndex(subsampleAtts[g], j, intervalArray));
                                } else {
                                    result.instance(0).setValue(j * numAttributes + g,
                                            FeatureSet.calcFeatureByIndex(subsampleAtts[g], interval[j][0],
                                                    interval[j][1], series));
                                }
                            }
                        }

                        double[] newProbs = tree.distributionForInstance(result.instance(0));
                        oobCounts[n]++;
                        for (int k = 0; k < newProbs.length; k++)
                            trainDistributions[n][k] += newProbs[k];
                    }
                }

                h.oobCounts = oobCounts;
                h.trainDistribution = trainDistributions;
                h.errorTime = System.nanoTime() - t1;
            }

            h.tree = tree;
            h.interval = interval;
            return h;
        }
    }

    /**
     * Class to hold data about a CIF tree when multi threading.
     */
    private static class MultiThreadPredictionHolder {
        int c;

        ArrayList<double[]> al;

        public MultiThreadPredictionHolder() {
        }
    }

    /**
     * Class to make a class prediction using a CIF tree when multi threading.
     */
    private class TreePredictionThread implements Callable<MultiThreadPredictionHolder> {
        int i;
        double[][] dimensions;
        Classifier tree;
        Instances testHolder;

        public TreePredictionThread(int i, double[][] dimensions, Classifier tree, Instances testHolder) {
            this.i = i;
            this.dimensions = dimensions;
            this.tree = tree;
            this.testHolder = testHolder;
        }

        @Override
        public MultiThreadPredictionHolder call() throws Exception {
            MultiThreadPredictionHolder h = new MultiThreadPredictionHolder();

            if (base instanceof ContinuousIntervalTree) {
                if (interpSavePath != null) {
                    ArrayList<double[]> al = new ArrayList<>();
                    h.c = (int) ((ContinuousIntervalTree) trees.get(i)).classifyInstance(dimensions, functions,
                            intervals.get(i), subsampleAtts.get(i), intervalDimensions.get(i), al);
                    h.al = al;
                } else {
                    h.c = (int) ((ContinuousIntervalTree) trees.get(i)).classifyInstance(dimensions, functions,
                            intervals.get(i), subsampleAtts.get(i), intervalDimensions.get(i));
                }
            } else {
                //Build transformed instance
                Catch22 c22 = new Catch22();
                c22.setOutlierNormalise(outlierNorm);

                for (int j = 0; j < numIntervals; j++) {
                    double[] series = dimensions[intervalDimensions.get(i)[j]];
                    double[] intervalArray = Arrays.copyOfRange(series, intervals.get(i)[j][0],
                            intervals.get(i)[j][1] + 1);

                    for (int g = 0; g < numAttributes; g++) {
                        if (subsampleAtts.get(i)[g] < 22) {
                            testHolder.instance(0).setValue(j * numAttributes + g,
                                    c22.getSummaryStatByIndex(subsampleAtts.get(i)[g], j, intervalArray));
                        } else {
                            testHolder.instance(0).setValue(j * numAttributes + g,
                                    FeatureSet.calcFeatureByIndex(subsampleAtts.get(i)[g], intervals.get(i)[j][0],
                                            intervals.get(i)[j][1], series));
                        }
                    }
                }

                h.c = (int) tree.classifyInstance(testHolder.instance(0));
            }

            return h;
        }
    }

    /**
     * CIF attributes as functions
     **/
    public static final Function<Interval, Double>[] functions = new Function[]{c22_0, c22_1, c22_2, c22_3, c22_4,
            c22_5, c22_6, c22_7, c22_8, c22_9, c22_10, c22_11, c22_12, c22_13, c22_14, c22_15, c22_16, c22_17, c22_18,
            c22_19, c22_20, c22_21, mean, stdev, slope};

    /**
     * Development tests for the CIF classifier.
     *
     * @param arg arguments, unused
     * @throws Exception if tests fail
     */
    public static void main(String[] arg) throws Exception {
        Instances[] data = DatasetLoading.sampleItalyPowerDemand(0);
        Instances train = data[0];
        Instances test = data[1];
        CIF c = new CIF();
        c.setSeed(0);
        c.estimateOwnPerformance = true;
        c.trainEstimateMethod = TrainEstimateMethod.OOB;
        double a;
        long t1 = System.nanoTime();
        c.buildClassifier(train);
        System.out.println("Train time=" + (System.nanoTime() - t1) * 1e-9);
        System.out.println("build ok: original atts = " + (train.numAttributes() - 1) + " new atts = "
                + (c.testHolder.numAttributes() - 1) + " num trees = " + c.trees.size() + " num intervals = " + c.numIntervals);
        System.out.println("recorded times: train time = " + (c.trainResults.getBuildTime() * 1e-9) + " estimate time = "
                + (c.trainResults.getErrorEstimateTime() * 1e-9)
                + " both = " + (c.trainResults.getBuildPlusEstimateTime() * 1e-9));
        a = ClassifierTools.accuracy(test, c);
        System.out.println("Test Accuracy = " + a);
        System.out.println("Train Accuracy = " + c.trainResults.getAcc());

        //Test Accuracy = 0.9650145772594753
        //Train Accuracy = 0.9701492537313433
    }
}
