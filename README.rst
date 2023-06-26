**This repository is not being proactively maintained or receiving new implementation currently** (This message updated 26/06/2023). **Feel free to open still bug reports, we may get around to fixing them eventually but response may be delayed. For the latest time series algorithms implemented and maintained by our group, see the Python based** `aeon <https://github.com/aeon-toolkit/aeon>`__ **toolkit.**

UEA Time Series Classification
==============================

.. image:: https://travis-ci.com/uea-machine-learning/tsml.svg?branch=master
    :target: https://travis-ci.com/uea-machine-learning/tsml

A `Weka <https://svn.cms.waikato.ac.nz/svn/weka/branches/stable-3-8/>`__-compatible Java toolbox for
**time series classification, clustering and transformation**. For the python sklearn-compatible version, see 
`aeon <https://github.com/aeon-toolkit/aeon>`__.

Find out more info about our broader work and dataset hosting for the UCR univariate and UEA multivariate time series classification archives on our `website <http://www.timeseriesclassification.com>`__.

This codebase is actively being developed for our research. The dev branch will contain the most up-to-date, but stable, code. 

Installation
------------
We are looking into deploying this project on Maven or Gradle in the future. For now there are two options:

* download the `jar file <http://timeseriesclassification.com/Downloads/tsml11_3_2020.jar>`__ and include as a dependency in your project, or you can run experiments through command line, see the `examples on running experiments <https://github.com/uea-machine-learning/tsml/blob/dev/src/main/java/examples/Ex04_ThoroughExperiments.java>`__
* fork or download the source files and include in a project in your favourite IDE you can then construct your own experiments (see our `examples <https://github.com/uea-machine-learning/tsml/tree/dev/src/main/java/examples>`__) and implement your own classifiers.

Overview
--------

This codebase mainly represents the implementation of different algorithms in a common framework, which at the time leading up to the `Great Time Series Classification Bake Off <https://link.springer.com/article/10.1007/s10618-016-0483-9>`__ in particular was a real problem, with implementations being in any of Python, C/C++, Matlab, R, Java, etc. or even combinations thereof. 

We therefore mainly provide implementations of different classifiers as well as experimental and results analysis pipelines with the hope of promoting and streamlining open source, easily comparable, and easily reproducible results, specifically within the TSC space. 

While they are obviously very important methods to study, we shall very likely not be implementing any kind of deep learning methods in our codebase, and leave those rightfully in the land of optimised languages and libraries for them. See `aeon <https://github.com/aeon-toolkit/aeon>`__ for implemented deep learning methods for time series data.

Our `examples <https://github.com/uea-machine-learning/tsml/tree/dev/src/main/java/examples>`__ run through the basics of using the code, however the basic layout of the codebase is this:

`evaluation/ <https://github.com/uea-machine-learning/tsml/tree/master/src/main/java/evaluation>`__ 
    contains classes for generating, storing and analysing the results of your experiments
    
`experiments/ <https://github.com/uea-machine-learning/tsml/tree/master/src/main/java/experiments>`__ 
    contains classes specifying the experimental pipelines we utilise, and lists of classifier and dataset specifications. The 'main' class is `Experiments.java <https://github.com/uea-machine-learning/tsml/blob/master/src/main/java/experiments/Experiments.java>`__, however other experiments classes exist for running on simulation datasets or for generating transforms of time series for later classification, such as with the Shapelet Transform. 

`tsml/ <https://github.com/uea-machine-learning/tsml/tree/master/src/main/java/tsml>`__ and `multivariate_timeseriesweka/ <https://github.com/uea-machine-learning/tsml/tree/master/src/main/java/multivariate_timeseriesweka>`__
    contain the TSC algorithms we have implemented, for univariate and multivariate classification respectively. 

`machine_learning/ <https://github.com/uea-machine-learning/tsml/tree/master/src/main/java/machine_learning>`__
    contains extra algorithm implementations that are not specific to TSC, such as generalised ensembles or classifier tuners. 

Implemented Algorithms
----------------------

Classifiers
```````````

The lists of implemented TSC algorithms shall continue to grow over time. These are all in addition to the standard Weka classifiers and non-TSC algorithms defined under the machine_learning package.

We have implemented the following bespoke classifiers for univariate, equal length time series classification:

================  ================  ==============  =================  ==============  ================
Distance Based    Dictionary Based  Kernel Based    Shapelet Based     Interval Based  Hybrids
================  ================  ==============  =================  ==============  ================
DD_DTW            BOSS              Arsenal         LearnShapelets     TSF             HIVE-COTE
DTD_C             cBOSS             ROCKET          ShapeletTransform  TSBF            Catch22
ElasticEnsemble   TDE                               FastShapelets      LPS
NN_CID            WEASEL                            ShapeletTree       CIF
SAX_1NN           SAXVSM                                               DrCIF
ProximityForest   SpatialBOSS                                          RISE
DTW_kNN           SAX_1NN                                              STSF
FastDTW           BafOfPatterns...
FastElasticEn...  BOSSC45
ShapeDTW_1NN      BoTSWEnsemble
ShapeDTW_SVM      BOSSSpatialPy...
SlowDTW_1NN
KNN
================  ================  ==============  =================  ==============  ================

And we have implemented the following bespoke classifiers for multivariate, equal length time series classification:

========  =============================
NN_ED_D   MultivariateShapeletTransform
NN_ED_I   ConcatenateClassifier
NN_DTW_D  MultivariateHiveCote
NN_DTW_I  WEASEL+MUSE
STC_D     MultivariateSingleEnsemble
NN_DTW_A  MultivariateAbstractClassifier
\         MultivariateAbstractEnsemble
========  =============================

Clusterers
``````````

Currently quite limited, aside from those already shipped with Weka.

============================ ====
UnsupervisedShapelets
K-Shape
DictClusterer
TTC
AbstractTimeSeriesCLusterer
============================ ====

Filters
```````````````````````

SimpleBatchFilters that take an Instances (the set of time series), transforms them
and returns a new Instances object.

===================  ===================  ===================
ACF                  ACF_PACF             ARMA
BagOfPatternsFilter  BinaryTransform      Clipping
Correlation          Cosine               DerivativeFilter
Differences          FFT                  Hilbert
MatrixProfile        NormalizeAttribute   NormalizeCase
PAA                  PACF                 PowerCepstrum
PowerSepstrum        RankOrder            RunLength
SAX                  Sine                 SummaryStats
===================  ===================  ===================

Transformers
We will be shifting over to a bespoke Transformer interface

=================== =======
ShapeletTransform
catch22
=================== =======

Paper-Supporting Branches
-------------------------

This project acts as the general open-source codebase for our research, especially the `Great Time Series Classification Bake Off <https://link.springer.com/article/10.1007/s10618-016-0483-9>`__. We are also trialling a process of creating stable branches in support of specific outputs. 

Current branches of this type are: 

* `paper/cawpe/ <https://github.com/uea-machine-learning/tsml/tree/paper/cawpe>`__ in support of `"A probabilistic classifier ensemble weighting scheme based on cross-validated accuracy estimates" <https://link.springer.com/article/10.1007/s10618-019-00638-y>`__

* `paper/cawpeExtension/ <https://github.com/uea-machine-learning/tsml/tree/paper/cawpeExtension>`__ in support of "Mixing hetero- and homogeneous models in weighted ensembles" (Accepted/in-press)

Contributors
------------

Lead: Anthony Bagnall (@TonyBagnall, `@tony_bagnall <https://twitter.com/tony_bagnall>`__, ajb@uea.ac.uk)

* James Large (@James-Large, `@jammylarge <https://twitter.com/jammylarge>`__, james.large@uea.ac.uk)
* Jason Lines (@jasonlines), 
* George Oastler (@goastler), 
* Matthew Middlehurst (@MatthewMiddlehurst, `@M_Middlehurst <https://twitter.com/M_Middlehurst>`__, m.middlehurst@uea.ac.uk),
* Michael Flynn (GitHub - `@MJFlynn <https://github.com/MJFlynn>`__, Twitter - `@M_J_Flynn <https://twitter.com/M_J_Flynn>`__, Email - Michael.Flynn@uea.ac.uk)
* Aaron Bostrom (@ABostrom, `@_Groshh_ <https://twitter.com/_Groshh_>`__, a.bostrom@uea.ac.uk), 
* Patrick Schäfer (@patrickzib)
* Chang Wei Tan (@ChangWeiTan)
* Alejandro Pasos Ruiz (a.pasos-ruiz@uea.ac.uk)
* Conor Egan (@c-eg)

We welcome anyone who would like to contribute their algorithms! 

License 
-------

GNU General Public License v3.0
