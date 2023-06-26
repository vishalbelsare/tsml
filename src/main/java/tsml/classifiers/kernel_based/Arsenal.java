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

package tsml.classifiers.kernel_based;

import evaluation.evaluators.CrossValidationEvaluator;
import evaluation.storage.ClassifierResults;
import experiments.data.DatasetLoading;
import machine_learning.classifiers.RidgeClassifierCV;
import tsml.classifiers.EnhancedAbstractClassifier;
import tsml.classifiers.MultiThreadable;
import tsml.classifiers.TrainTimeContractable;
import tsml.transformers.ROCKET;
import utilities.ClassifierTools;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Randomizable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Contractable classifier making use of the ROCKET transformer.
 * <p>
 * Transform based on sktime python implementation by the author:
 * https://github.com/alan-turing-institute/sktime/blob/master/sktime/transformers/series_as_features/rocket.py
 *
 * @author Matthew Middlehurst
 */
public class Arsenal extends EnhancedAbstractClassifier implements TrainTimeContractable, MultiThreadable {

    private int numKernels = 2000;
    private int ensembleSize = 25;
    private boolean normalise = true;
    private Classifier cls = new RidgeClassifierCV();

    private boolean bagging = false;
    private double[] oobCounts;
    private double[][] trainDistributions;

    private long trainContractTimeNanos = 0;
    private boolean trainTimeContract = false;
    private int maxEnsembleSize = 200;

    private boolean multithreading = false;
    private int threads;

    private Classifier[] classifiers;
    private ROCKET[] rockets;
    private double weightSum;
    private Instances header;

    public Arsenal() {
        super(CAN_ESTIMATE_OWN_PERFORMANCE);
    }

    @Override
    public String getParameters() {
        int cl = classifiers == null ? 0 : classifiers.length;
        return super.getParameters() + ",numKernels," + numKernels + ",normalise," + normalise +
                ",ensembleSize," + cl + ",trainContract," + trainTimeContract + ",contractTime," +
                trainContractTimeNanos;
    }

    @Override
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

    public void setNumKernels(int numKernels) {
        this.numKernels = numKernels;
    }

    public void setNormalise(boolean normalise) {
        this.normalise = normalise;
    }

    public void setClassifier(Classifier cls) {
        this.cls = cls;
    }

    public void setEnsembleSize(int ensembleSize) {
        this.ensembleSize = ensembleSize;
    }

    public void setBagging(boolean bagging) {
        this.bagging = bagging;
    }

    @Override
    public void setTrainTimeLimit(long time) {
        trainContractTimeNanos = time;
        trainTimeContract = true;
    }

    @Override
    public boolean withinTrainContract(long start) {
        if (trainContractTimeNanos <= 0) return true; //Not contracted
        int d = getEstimateOwnPerformance() && trainEstimateMethod == TrainEstimateMethod.OOB && !bagging ? 2 : 1;
        return System.nanoTime() - start < trainContractTimeNanos / d;
    }

    @Override
    public void enableMultiThreading(int numThreads) {
        multithreading = true;
        threads = numThreads;
    }

    @Override
    public void buildClassifier(Instances data) throws Exception {
        super.buildClassifier(data);
        trainResults.setBuildTime(System.nanoTime());
        getCapabilities().testWithFail(data);

        int numInstances = data.numInstances();

        if (multithreading && cls instanceof MultiThreadable)
            ((MultiThreadable) cls).enableMultiThreading(threads);

        if (trainTimeContract) ensembleSize = maxEnsembleSize;

        int numFolds = -1;
        if (getEstimateOwnPerformance()) {
            trainDistributions = new double[numInstances][numClasses];
            if (bagging) {
                oobCounts = new double[numInstances];
            } else {
                numFolds = Math.min(data.numInstances(), 10);
            }
        }

        ArrayList<Classifier> tempCls = new ArrayList<>();
        ArrayList<ROCKET> tempROCKET = new ArrayList<>();
        weightSum = 0;

        int i = 0;
        while (i < ensembleSize && withinTrainContract(trainResults.getBuildTime())) {
            ROCKET r = new ROCKET();
            r.setNumKernels(numKernels);
            r.setNormalise(normalise);
            if (seedClassifier) r.setSeed(seed + (i + 1) * 47);

            if (multithreading) {
                r.enableMultiThreading(threads);
            }

            //If bagging find instances with replacement
            boolean[] inBag = null;
            Instances newData;
            if (bagging) {
                newData = new Instances(data, numInstances);
                inBag = new boolean[numInstances];

                for (int n = 0; n < numInstances; n++) {
                    int idx = rand.nextInt(numInstances);
                    newData.add(data.get(idx));
                    inBag[idx] = true;
                }
            } else {
                newData = data;
            }

            Instances transformedData = r.fitTransform(newData);
            if (header == null) header = new Instances(transformedData, 0);

            Classifier c = AbstractClassifier.makeCopy(cls);
            if (seedClassifier && c instanceof Randomizable) {
                ((Randomizable) c).setSeed(seed + (i + 1) * 47);
            }

            c.buildClassifier(transformedData);

            tempCls.add(c);
            tempROCKET.add(r);

            double w = cls instanceof RidgeClassifierCV ? Math.pow(((RidgeClassifierCV) c).getBestScore(), 4) : 1;
            weightSum += w;

            if (getEstimateOwnPerformance()) {
                long t1 = System.nanoTime();

                if (bagging) {
                    for (int n = 0; n < numInstances; n++) {
                        if (inBag[n])
                            continue;

                        Instance inst = r.transform(data.get(n));
                        inst.setDataset(transformedData);
                        double[] newProbs = c.distributionForInstance(inst);
                        oobCounts[n] += w;
                        for (int j = 0; j < newProbs.length; j++)
                            trainDistributions[n][j] += newProbs[j] * w;
                    }
                } else if (trainEstimateMethod != TrainEstimateMethod.OOB) {
                    CrossValidationEvaluator cv = new CrossValidationEvaluator();
                    if (seedClassifier)
                        cv.setSeed(seed + (i + 1) * 67);
                    cv.setNumFolds(numFolds);

                    Classifier cvCls = AbstractClassifier.makeCopy(cls);
                    if (seedClassifier && cls instanceof Randomizable)
                        ((Randomizable) cvCls).setSeed(seed + (i + 1) * 67);

                    ClassifierResults results = cv.evaluate(cvCls, transformedData);
                    for (int n = 0; n < numInstances; n++) {
                        double[] dist = results.getProbabilityDistribution(n);
                        for (int j = 0; j < trainDistributions[n].length; j++)
                            trainDistributions[n][j] += dist[j] * w;
                    }
                }

                trainResults.setErrorEstimateTime(trainResults.getErrorEstimateTime() + (System.nanoTime() - t1));
            }

            i++;
        }

        classifiers = new Classifier[tempCls.size()];
        classifiers = tempCls.toArray(classifiers);
        rockets = new ROCKET[tempROCKET.size()];
        rockets = tempROCKET.toArray(rockets);

        trainResults.setTimeUnit(TimeUnit.NANOSECONDS);
        trainResults.setBuildTime(System.nanoTime() - trainResults.getBuildTime());
        if (getEstimateOwnPerformance()) {
            long est1 = System.nanoTime();
            findEnsembleTrainEstimate(data);
            long est2 = System.nanoTime();
            trainResults.setErrorEstimateTime(est2 - est1 + trainResults.getErrorEstimateTime());
        }
        trainResults.setBuildPlusEstimateTime(trainResults.getBuildTime() + trainResults.getErrorEstimateTime());
        trainResults.setParas(getParameters());
        printLineDebug("*************** Finished Arsenal Build with "+rockets.length+" rockets in " +
                (trainResults.getBuildTime()/1000000000/60/60.0) + " hours, Train+Estimate time = "+(trainResults.getBuildPlusEstimateTime()/1000000000/60/60.0)+" hours ***************");

    }

    private void findEnsembleTrainEstimate(Instances data) throws Exception {
        if (bagging) {
            double[] preds = new double[data.numInstances()];
            double[] actuals = new double[data.numInstances()];
            long[] predTimes = new long[data.numInstances()]; //Dummy variable, need something
            for (int j = 0; j < data.numInstances(); j++) {
                long predTime = System.nanoTime();
                if (oobCounts[j] == 0)
                    Arrays.fill(trainDistributions[j], 1.0/trainDistributions[j].length);
                else
                    for (int k = 0; k < trainDistributions[j].length; k++)
                        trainDistributions[j][k] /= oobCounts[j];
                preds[j] = findIndexOfMax(trainDistributions[j], rand);
                actuals[j] = data.get(j).classValue();
                predTimes[j] = System.nanoTime() - predTime;
            }
            trainResults.addAllPredictions(actuals, preds, trainDistributions, predTimes, null);
            trainResults.setDatasetName(data.relationName());
            trainResults.setSplit("train");
            trainResults.setFoldID(seed);
            trainResults.setEstimatorName("ArsenalOOB");
            trainResults.setErrorEstimateMethod("OOB");
        } else if (trainEstimateMethod == TrainEstimateMethod.CV || trainEstimateMethod == TrainEstimateMethod.NONE ||
                trainEstimateMethod == TrainEstimateMethod.TRAIN) {
            double[] preds = new double[data.numInstances()];
            double[] actuals = new double[data.numInstances()];
            long[] predTimes = new long[data.numInstances()]; //Dummy variable, need something
            for (int j = 0; j < data.numInstances(); j++) {
                long predTime = System.nanoTime();
                for (int k = 0; k < trainDistributions[j].length; k++)
                    trainDistributions[j][k] /= weightSum;
                preds[j] = findIndexOfMax(trainDistributions[j], rand);
                actuals[j] = data.get(j).classValue();
                predTimes[j] = System.nanoTime() - predTime;
            }
            trainResults.addAllPredictions(actuals, preds, trainDistributions, predTimes, null);
            trainResults.setDatasetName(data.relationName());
            trainResults.setSplit("train");
            trainResults.setFoldID(seed);
            trainResults.setEstimatorName("ArsenalCV");
            trainResults.setErrorEstimateMethod("CV_10");
        } else if (trainEstimateMethod == TrainEstimateMethod.OOB) {
            Arsenal ar = new Arsenal();
            ar.copyParameters(this);
            ar.setSeed(seed * 5);
            ar.setEstimateOwnPerformance(true);
            if (trainTimeContract)
                ar.setTrainTimeLimit((long)(trainContractTimeNanos / 10 * 4.5));
            ar.bagging = true;
            ar.buildClassifier(data);
            long tt = trainResults.getBuildTime();
            trainResults = ar.trainResults;
            trainResults.setBuildTime(tt);
            trainResults.setEstimatorName("ArsenalOOB");
            trainResults.setErrorEstimateMethod("OOB");
        }
    }

    @Override
    public double classifyInstance(Instance instance) throws Exception {
        double[] probs = distributionForInstance(instance);
        return findIndexOfMax(probs, rand);
    }

    public double[] distributionForInstance(Instance instance) throws Exception {
        double[] probs = new double[header.numClasses()];
        for (int i = 0; i < classifiers.length; i++) {
            Instance transformedInst = rockets[i].transform(instance);
            transformedInst.setDataset(header);
            double pred = classifiers[i].classifyInstance(transformedInst);
            double w = cls instanceof RidgeClassifierCV ?
                    Math.pow(((RidgeClassifierCV) classifiers[i]).getBestScore(), 4) : 1;
            probs[(int) pred] += w;
        }

        for (int i = 0; i < probs.length; i++) probs[i] /= weightSum;
        return probs;
    }

    private void copyParameters(Arsenal other) {
        this.numKernels = other.numKernels;
        this.ensembleSize = other.ensembleSize;
        this.normalise = other.normalise;
        this.cls = other.cls;
        this.bagging = other.bagging;
        this.trainContractTimeNanos = other.trainContractTimeNanos;
        this.trainTimeContract = other.trainTimeContract;
    }

    public static void main(String[] args) throws Exception {
        int fold = 0;

        Instances[] data = DatasetLoading.sampleItalyPowerDemand(fold);
        Instances train = data[0];
        Instances test = data[1];

        Instances[] data2 = DatasetLoading.sampleERing(fold);
        Instances train2 = data2[0];
        Instances test2 = data2[1];

        Arsenal c;
        double accuracy;

        c = new Arsenal();
        c.seed = fold;
        c.setTrainEstimateMethod(TrainEstimateMethod.OOB);
        c.setEstimateOwnPerformance(true);
        c.buildClassifier(train);
        accuracy = ClassifierTools.accuracy(test, c);

        System.out.println("Arsenal accuracy on ItalyPowerDemand fold " + fold + " = " + accuracy);
        System.out.println("Train accuracy on ItalyPowerDemand fold " + fold + " = " + c.trainResults.getAcc());
        System.out.println("Build time on ItalyPowerDemand fold " + fold + " = " +
                TimeUnit.SECONDS.convert(c.trainResults.getBuildTime(), TimeUnit.NANOSECONDS) + " seconds");
        System.out.println("Estimate time on ItalyPowerDemand fold " + fold + " = " +
                TimeUnit.SECONDS.convert(c.trainResults.getErrorEstimateTime(), TimeUnit.NANOSECONDS) + " seconds");

        c = new Arsenal();
        c.seed = fold;
        c.buildClassifier(train2);
        accuracy = ClassifierTools.accuracy(test2, c);

        System.out.println("Arsenal accuracy on ERing fold " + fold + " = " + accuracy);
        System.out.println("Build time on ERing fold " + fold + " = " +
                TimeUnit.SECONDS.convert(c.trainResults.getBuildTime(), TimeUnit.NANOSECONDS) + " seconds");
    }
}
