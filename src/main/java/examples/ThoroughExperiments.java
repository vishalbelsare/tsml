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

package examples;

import experiments.ClassifierExperiments;
import experiments.ExperimentalArguments;

/**
 * Examples showing how to use the ClassifierExperiments class
 * 
 * @author James Large (james.large@uea.ac.uk)
 */
public class ThoroughExperiments {
    
    public static void main(String[] args) throws Exception {
        // NOTE: If you want to run this file, you'll need to define an 
        // acceptable location to write some small files as examples: 
        String resultsPath = "C:/Temp/ueatscExamples/";
        
        
        
        // We've seen how to load data, construct a classifer, and evaluate it in our own code
        // Our main use case however is running distributed experiments of many classifiers
        // over many datasets and resamples of each dataset in order to compare them.
        
        // The experiments class handles this main use case. It will take a classifier, dataset,
        // resample id, and read/write locations at minimum, perform the evaluation, and write 
        // the results in the ClassifierResults format.
        
        ///////// Running a job from command line: 
        
        String[] exampleMinimalArgs = { 
            "--dataPath=src/main/java/experiments/data/tsc/", // where to read data from
            "--resultsPath="+resultsPath,        // where to write results to
            "--classifierName=RandF",           // loaded from ClassifierLists.setClassifier, see Ex02_Classifiers
            "--datasetName=ItalyPowerDemand", // loaded using sampleDataset, see Ex01_DataHandling
            "--fold=1", // used as the seed. Because of our cluster, this is one-indexed on input, but immediately decremented to be zero-indexed
           
            // above are the required args, all others are optional
            // for this run, we'll also forceEvaluation since, the experiment
            // by default will abort if the result file already exists
            "--force=true"
        };
        
        ClassifierExperiments.main(exampleMinimalArgs);
        // or actually from command line e.g.: 
        // java -jar ueatsc.jar -dp=src/main/java/experiments/data/tsc/ ...
        
        
        
        
        
        
        ///////// Running a job from code: 
        
        // When running locally from code, it may be easier to just set up the 
        // ExperimentalArguments object yourself
        
        ExperimentalArguments exp = new ExperimentalArguments();
        exp.dataReadLocation = "src/main/java/experiments/data/tsc/";
        exp.resultsWriteLocation = resultsPath;
        exp.estimatorName = "RandF";
        exp.datasetName = "ItalyPowerDemand";
        exp.foldId = 0;  // note that since we're now setting the fold directly, we can resume zero-indexing
        
        // here, we wont force the evaluation. see the difference
        ClassifierExperiments.setupAndRunExperiment(exp);
        
        
        
        
        
        
        
        // Running many jobs from code: 
        
        // Here, we'll set up to run the jobs threaded. You can also easily imagine
        // just setting up a loop over each classifier, dataset, fold and calling setupAndRunExperiment above
        
        String[] classifiers = { "SVML", "ED", "C45" }; // with entries in ClassifierLists.setClassifier, see Ex02_Classifiers
        String[] datasets = { "ItalyPowerDemand", "Beef" }; // both available at the dataReadLocation
        int numFolds = 3;
        
        ExperimentalArguments expThreaded = new ExperimentalArguments();
        expThreaded.dataReadLocation = "src/main/java/experiments/data/tsc/"; // set the common data read location
        expThreaded.resultsWriteLocation = resultsPath;                       // set the common results write location
        // set any other common settings you want here, e.g. force
        // classifier, dataset, fold shall be assigned internally across threads
        
        // will use one thread per core by default
        ClassifierExperiments.setupAndRunMultipleExperimentsThreaded(expThreaded, classifiers, null, datasets, 0, numFolds);
        
    }
    
}
